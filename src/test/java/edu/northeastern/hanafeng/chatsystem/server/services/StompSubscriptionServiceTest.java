package edu.northeastern.hanafeng.chatsystem.server.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.reset;

@ExtendWith(MockitoExtension.class)
class StompSubscriptionServiceTest {

    @Mock
    private ConsumerDiscoveryService consumerDiscoveryService;

    @Mock
    private WebSocketStompClient webSocketStompClient;

    @Mock
    private StompSessionHandlerAdapter sessionHandler;

    @Mock
    private StompSession stompSession;

    @Mock
    private StompSession.Subscription subscription;

    private StompSubscriptionService stompSubscriptionService;

    @BeforeEach
    void setUp() {
        // Reset mocks to clear any previous interactions
        reset(webSocketStompClient, consumerDiscoveryService, stompSession, sessionHandler, subscription);

        stompSubscriptionService = new StompSubscriptionService(consumerDiscoveryService, webSocketStompClient);
        ReflectionTestUtils.setField(stompSubscriptionService, "topicPrefix", "/topic/room");
        stompSubscriptionService.registerHandler(sessionHandler);
    }

    @Test
    void testSubscribe_NoHandlerRegistered() {
        // Given
        StompSubscriptionService serviceWithoutHandler = new StompSubscriptionService(consumerDiscoveryService, webSocketStompClient);
        ReflectionTestUtils.setField(serviceWithoutHandler, "topicPrefix", "/topic/room");

        // When - subscribe is now async, so it just logs an error instead of throwing
        serviceWithoutHandler.subscribe("room1");

        // Then - no exception thrown, method returns immediately
        // The error is logged instead of throwing an exception
        verify(consumerDiscoveryService, never()).findConsumerEndpoint(anyString());
    }

    @Test
    void testSubscribe_NoConsumerEndpointFound() {
        // Given
        when(consumerDiscoveryService.findConsumerEndpoint("room1")).thenReturn(null);

        // When
        stompSubscriptionService.subscribe("room1");

        // Then - should return early without error
        verify(consumerDiscoveryService).findConsumerEndpoint("room1");
    }

    @Test
    void testSubscribe_AlreadySubscribed() {
        // Given
        when(consumerDiscoveryService.findConsumerEndpoint("room1"))
                .thenReturn("ws://consumer1:8080");

        // Simulate first subscription by manipulating internal state
        ReflectionTestUtils.invokeMethod(stompSubscriptionService, "addRoom", "ws://consumer1:8080", "room1");

        // When
        stompSubscriptionService.subscribe("room1");

        // Then - should detect duplicate and return early
        verify(consumerDiscoveryService).findConsumerEndpoint("room1");
        // Should not attempt to create session or subscribe
        verify(stompSession, never()).isConnected();
        verify(stompSession, never()).subscribe(anyString(), any());
    }

    @Test
    void testUnsubscribe_SubscriptionExists() {
        // Given
        when(consumerDiscoveryService.findConsumerEndpoint("room1"))
                .thenReturn("ws://consumer1:8080");

        // Add subscription to internal map
        java.util.Map<String, StompSession.Subscription> subscriptions =
            (java.util.Map<String, StompSession.Subscription>) ReflectionTestUtils.getField(stompSubscriptionService, "subscriptions");
        subscriptions.put("room1", subscription);

        // Add room to tracking
        ReflectionTestUtils.invokeMethod(stompSubscriptionService, "addRoom", "ws://consumer1:8080", "room1");

        // When
        stompSubscriptionService.unsubscribe("room1");

        // Then
        verify(subscription).unsubscribe();
    }

    @Test
    void testUnsubscribe_NoSubscriptionFound() {
        // Given
        when(consumerDiscoveryService.findConsumerEndpoint("room1"))
                .thenReturn("ws://consumer1:8080");

        // When
        stompSubscriptionService.unsubscribe("room1");

        // Then - should handle gracefully without error
        verify(consumerDiscoveryService).findConsumerEndpoint("room1");
    }

    @Test
    void testUnsubscribe_NoConsumerEndpoint() {
        // Given
        when(consumerDiscoveryService.findConsumerEndpoint("room1")).thenReturn(null);

        // Add subscription to internal map
        java.util.Map<String, StompSession.Subscription> subscriptions =
            (java.util.Map<String, StompSession.Subscription>) ReflectionTestUtils.getField(stompSubscriptionService, "subscriptions");
        subscriptions.put("room1", subscription);

        // When
        stompSubscriptionService.unsubscribe("room1");

        // Then - should unsubscribe but log warning about missing endpoint
        verify(subscription).unsubscribe();
        verify(consumerDiscoveryService).findConsumerEndpoint("room1");
    }

    @Test
    void testAddRoom_NewRoom() {
        // When
        boolean result = ReflectionTestUtils.invokeMethod(
            stompSubscriptionService, "addRoom", "ws://consumer1:8080", "room1");

        // Then
        assertTrue(result);
    }

    @Test
    void testAddRoom_DuplicateRoom() {
        // Given
        ReflectionTestUtils.invokeMethod(stompSubscriptionService, "addRoom", "ws://consumer1:8080", "room1");

        // When
        boolean result = ReflectionTestUtils.invokeMethod(
            stompSubscriptionService, "addRoom", "ws://consumer1:8080", "room1");

        // Then
        assertFalse(result);
    }

    @Test
    void testRemoveRoom_LastRoom() {
        // Given
        ReflectionTestUtils.invokeMethod(stompSubscriptionService, "addRoom", "ws://consumer1:8080", "room1");

        // When
        boolean result = ReflectionTestUtils.invokeMethod(
            stompSubscriptionService, "removeRoom", "ws://consumer1:8080", "room1");

        // Then
        assertTrue(result); // Should indicate no more rooms
    }

    @Test
    void testRemoveRoom_OtherRoomsExist() {
        // Given
        ReflectionTestUtils.invokeMethod(stompSubscriptionService, "addRoom", "ws://consumer1:8080", "room1");
        ReflectionTestUtils.invokeMethod(stompSubscriptionService, "addRoom", "ws://consumer1:8080", "room2");

        // When
        boolean result = ReflectionTestUtils.invokeMethod(
            stompSubscriptionService, "removeRoom", "ws://consumer1:8080", "room1");

        // Then
        assertFalse(result); // Should indicate other rooms still exist
    }

    @Test
    void testRemoveRoom_NoRoomsTracked() {
        // When
        boolean result = ReflectionTestUtils.invokeMethod(
            stompSubscriptionService, "removeRoom", "ws://consumer1:8080", "room1");

        // Then
        assertTrue(result); // Should return true (no rooms = should disconnect)
    }

    @Test
    void testRegisterHandler_WithExistingSubscriptions() {
        // Given
        ReflectionTestUtils.invokeMethod(stompSubscriptionService, "addRoom", "ws://consumer1:8080", "room1");
        StompSubscriptionService newService = new StompSubscriptionService(consumerDiscoveryService, webSocketStompClient);
        ReflectionTestUtils.setField(newService, "topicPrefix", "/topic/room");

        // Add room to new service
        ReflectionTestUtils.invokeMethod(newService, "addRoom", "ws://consumer1:8080", "room1");

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            newService.registerHandler(sessionHandler);
        });
    }

}
