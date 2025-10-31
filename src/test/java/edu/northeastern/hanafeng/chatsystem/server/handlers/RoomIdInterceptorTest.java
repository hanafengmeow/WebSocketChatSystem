package edu.northeastern.hanafeng.chatsystem.server.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomIdInterceptorTest {

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    private RoomIdInterceptor interceptor;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        interceptor = new RoomIdInterceptor();
        ReflectionTestUtils.setField(interceptor, "chatEndpoint", "/ws/chat/{roomId}");
        ReflectionTestUtils.setField(interceptor, "maxRooms", 100);
        attributes = new HashMap<>();
    }

    @Test
    void testBeforeHandshake_ValidRoomId() throws Exception {
        // Given
        String validPath = "/ws/chat/50";
        when(request.getURI()).thenReturn(new URI(validPath));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertTrue(result);
        assertEquals("50", attributes.get("roomId"));
    }

    @Test
    void testBeforeHandshake_RoomIdIsOne() throws Exception {
        // Given
        String validPath = "/ws/chat/1";
        when(request.getURI()).thenReturn(new URI(validPath));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertTrue(result);
        assertEquals("1", attributes.get("roomId"));
    }

    @Test
    void testBeforeHandshake_RoomIdIsMaxRooms() throws Exception {
        // Given
        String validPath = "/ws/chat/100";
        when(request.getURI()).thenReturn(new URI(validPath));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertTrue(result);
        assertEquals("100", attributes.get("roomId"));
    }

    @Test
    void testBeforeHandshake_RoomIdZero() throws Exception {
        // Given
        String invalidPath = "/ws/chat/0";
        when(request.getURI()).thenReturn(new URI(invalidPath));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertFalse(result);
        assertNull(attributes.get("roomId"));
    }

    @Test
    void testBeforeHandshake_RoomIdNegative() throws Exception {
        // Given
        String invalidPath = "/ws/chat/-5";
        when(request.getURI()).thenReturn(new URI(invalidPath));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertFalse(result);
        assertNull(attributes.get("roomId"));
    }

    @Test
    void testBeforeHandshake_RoomIdExceedsMaxRooms() throws Exception {
        // Given
        String invalidPath = "/ws/chat/101";
        when(request.getURI()).thenReturn(new URI(invalidPath));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertFalse(result);
        assertNull(attributes.get("roomId"));
    }

    @Test
    void testBeforeHandshake_RoomIdNotNumeric() throws Exception {
        // Given
        String invalidPath = "/ws/chat/abc";
        when(request.getURI()).thenReturn(new URI(invalidPath));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertFalse(result);
        assertNull(attributes.get("roomId"));
    }

    @Test
    void testBeforeHandshake_RoomIdEmpty() throws Exception {
        // Given
        String invalidPath = "/ws/chat/";
        when(request.getURI()).thenReturn(new URI(invalidPath));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertFalse(result);
        assertNull(attributes.get("roomId"));
    }

    @Test
    void testBeforeHandshake_RoomIdWithSpecialCharacters() throws Exception {
        // Given
        String invalidPath = "/ws/chat/12@34";
        when(request.getURI()).thenReturn(new URI(invalidPath));

        // When
        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // Then
        assertFalse(result);
        assertNull(attributes.get("roomId"));
    }

    @Test
    void testAfterHandshake_NoException() {
        // When
        interceptor.afterHandshake(request, response, wsHandler, null);

        // Then - should complete without throwing exception
    }

    @Test
    void testAfterHandshake_WithException() {
        // Given
        Exception exception = new RuntimeException("Test exception");

        // When
        interceptor.afterHandshake(request, response, wsHandler, exception);

        // Then - should log error but not throw exception
    }
}
