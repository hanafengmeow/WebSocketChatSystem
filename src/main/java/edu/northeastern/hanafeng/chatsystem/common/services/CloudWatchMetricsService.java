package edu.northeastern.hanafeng.chatsystem.common.services;

import edu.northeastern.hanafeng.chatsystem.common.utils.EnvironmentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudWatchMetricsService {

    private final CloudWatchClient cloudWatchClient;
    private final EnvironmentUtils environmentUtils;

    @Value("${websocketchat.cloudwatch.namespace}")
    private String namespace;

    @Value("${websocketchat.cloudwatch.storage-resolution}")
    private int storageResolution;

    @Async
    public void recordMetric(String metricName, double value) {
        try {
            Dimension hostnameDimension = Dimension.builder()
                    .name("Hostname")
                    .value(environmentUtils.getHostname())
                    .build();

            MetricDatum datum = MetricDatum.builder()
                    .metricName(metricName)
                    .unit(StandardUnit.COUNT)
                    .value(value)
                    .timestamp(Instant.now())
                    .dimensions(hostnameDimension)
                    .storageResolution(storageResolution)
                    .build();

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(namespace)
                    .metricData(datum)
                    .build();

            cloudWatchClient.putMetricData(request);
            log.info("Published CloudWatch metric: {} = {} to namespace: {} with hostname: {}",
                     metricName, value, namespace, environmentUtils.getHostname());
        } catch (Exception e) {
            log.error("Failed to publish CloudWatch metric: {} to namespace: {}", metricName, namespace, e);
        }
    }
}
