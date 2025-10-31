package edu.northeastern.hanafeng.chatsystem.common.services;

import edu.northeastern.hanafeng.chatsystem.common.utils.EnvironmentUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudWatchMetricsServiceTest {

    @Mock
    private CloudWatchClient cloudWatchClient;

    @Mock
    private EnvironmentUtils environmentUtils;

    private CloudWatchMetricsService service;

    @BeforeEach
    void setUp() {
        service = new CloudWatchMetricsService(cloudWatchClient, environmentUtils);
        ReflectionTestUtils.setField(service, "namespace", "WebSocketChat");
        ReflectionTestUtils.setField(service, "storageResolution", 60);

        when(environmentUtils.getHostname()).thenReturn("test-host");
    }

    @Test
    void testRecordMetric_Success() {
        // Given
        String metricName = "TestMetric";
        double value = 42.0;

        // When
        service.recordMetric(metricName, value);

        // Then
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(captor.capture());

        PutMetricDataRequest request = captor.getValue();
        assertEquals("WebSocketChat", request.namespace());
        assertEquals(1, request.metricData().size());

        MetricDatum datum = request.metricData().get(0);
        assertEquals("TestMetric", datum.metricName());
        assertEquals(42.0, datum.value());
        assertEquals(StandardUnit.COUNT, datum.unit());
        assertEquals(60, datum.storageResolution());
        assertNotNull(datum.timestamp());

        assertEquals(1, datum.dimensions().size());
        Dimension dimension = datum.dimensions().get(0);
        assertEquals("Hostname", dimension.name());
        assertEquals("test-host", dimension.value());
    }

    @Test
    void testRecordMetric_DifferentMetricNames() {
        // When
        service.recordMetric("Metric1", 10.0);
        service.recordMetric("Metric2", 20.0);

        // Then
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient, times(2)).putMetricData(captor.capture());

        assertEquals("Metric1", captor.getAllValues().get(0).metricData().get(0).metricName());
        assertEquals("Metric2", captor.getAllValues().get(1).metricData().get(0).metricName());
    }

    @Test
    void testRecordMetric_DifferentValues() {
        // When
        service.recordMetric("TestMetric", 1.5);

        // Then
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(captor.capture());

        assertEquals(1.5, captor.getValue().metricData().get(0).value());
    }

    @Test
    void testRecordMetric_CloudWatchException() {
        // Given
        when(cloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenThrow(CloudWatchException.builder().message("CloudWatch error").build());

        // When/Then - should not throw exception (logs error internally)
        assertDoesNotThrow(() -> service.recordMetric("TestMetric", 10.0));

        verify(cloudWatchClient).putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    void testRecordMetric_GenericException() {
        // Given
        when(cloudWatchClient.putMetricData(any(PutMetricDataRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> service.recordMetric("TestMetric", 5.0));

        verify(cloudWatchClient).putMetricData(any(PutMetricDataRequest.class));
    }

    @Test
    void testRecordMetric_UsesCorrectNamespace() {
        // Given
        ReflectionTestUtils.setField(service, "namespace", "CustomNamespace");

        // When
        service.recordMetric("Metric", 1.0);

        // Then
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(captor.capture());

        assertEquals("CustomNamespace", captor.getValue().namespace());
    }

    @Test
    void testRecordMetric_UsesCorrectStorageResolution() {
        // Given
        ReflectionTestUtils.setField(service, "storageResolution", 1);

        // When
        service.recordMetric("Metric", 1.0);

        // Then
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(captor.capture());

        assertEquals(1, captor.getValue().metricData().get(0).storageResolution());
    }

    @Test
    void testRecordMetric_ZeroValue() {
        // When
        service.recordMetric("ZeroMetric", 0.0);

        // Then
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(captor.capture());

        assertEquals(0.0, captor.getValue().metricData().get(0).value());
    }

    @Test
    void testRecordMetric_NegativeValue() {
        // When
        service.recordMetric("NegativeMetric", -5.0);

        // Then
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(cloudWatchClient).putMetricData(captor.capture());

        assertEquals(-5.0, captor.getValue().metricData().get(0).value());
    }
}
