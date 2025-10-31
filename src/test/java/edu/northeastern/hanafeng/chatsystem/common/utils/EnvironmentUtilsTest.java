package edu.northeastern.hanafeng.chatsystem.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentUtilsTest {

    @Test
    void testGetHostname_ReturnsNonNull() {
        // Given
        EnvironmentUtils environmentUtils = new EnvironmentUtils();

        // When
        String hostname = environmentUtils.getHostname();

        // Then
        assertNotNull(hostname);
        assertFalse(hostname.isEmpty());
    }

    @Test
    void testGetInternalIpAddress_ReturnsNonNull() {
        // Given
        EnvironmentUtils environmentUtils = new EnvironmentUtils();

        // When
        String ipAddress = environmentUtils.getInternalIpAddress();

        // Then
        assertNotNull(ipAddress);
        assertFalse(ipAddress.isEmpty());
    }

    @Test
    void testGetHostname_ReturnsValidHostnameOrFallback() {
        // Given
        EnvironmentUtils environmentUtils = new EnvironmentUtils();

        // When
        String hostname = environmentUtils.getHostname();

        // Then - should be either a valid hostname or "unknown"
        assertTrue(hostname.length() > 0);
        // Hostname should not contain invalid characters
        assertFalse(hostname.contains("\n"));
        assertFalse(hostname.contains("\r"));
    }

    @Test
    void testGetInternalIpAddress_ReturnsValidIpOrFallback() {
        // Given
        EnvironmentUtils environmentUtils = new EnvironmentUtils();

        // When
        String ipAddress = environmentUtils.getInternalIpAddress();

        // Then - should be either a valid IP or "127.0.0.1"
        assertTrue(ipAddress.length() > 0);
        // IP address should contain dots (IPv4 format) or colons (IPv6 format)
        assertTrue(ipAddress.contains(".") || ipAddress.contains(":"));
    }

    @Test
    void testEnvironmentUtils_ValuesAreImmutable() {
        // Given
        EnvironmentUtils environmentUtils = new EnvironmentUtils();

        // When
        String hostname1 = environmentUtils.getHostname();
        String hostname2 = environmentUtils.getHostname();
        String ip1 = environmentUtils.getInternalIpAddress();
        String ip2 = environmentUtils.getInternalIpAddress();

        // Then - should return same values on multiple calls
        assertEquals(hostname1, hostname2);
        assertEquals(ip1, ip2);
    }

    @Test
    void testMultipleInstances_ReturnSameValues() {
        // Given
        EnvironmentUtils utils1 = new EnvironmentUtils();
        EnvironmentUtils utils2 = new EnvironmentUtils();

        // When/Then - both instances should resolve to same hostname and IP
        assertEquals(utils1.getHostname(), utils2.getHostname());
        assertEquals(utils1.getInternalIpAddress(), utils2.getInternalIpAddress());
    }

    @Test
    void testGetHostname_NotEmpty() {
        // Given
        EnvironmentUtils environmentUtils = new EnvironmentUtils();

        // When
        String hostname = environmentUtils.getHostname();

        // Then
        assertNotEquals("", hostname);
        assertTrue(hostname.length() > 0);
    }

    @Test
    void testGetInternalIpAddress_NotEmpty() {
        // Given
        EnvironmentUtils environmentUtils = new EnvironmentUtils();

        // When
        String ipAddress = environmentUtils.getInternalIpAddress();

        // Then
        assertNotEquals("", ipAddress);
        assertTrue(ipAddress.length() > 0);
    }
}
