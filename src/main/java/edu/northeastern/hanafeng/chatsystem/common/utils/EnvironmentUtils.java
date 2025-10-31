package edu.northeastern.hanafeng.chatsystem.common.utils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Getter
@Component
@Slf4j
public class EnvironmentUtils {

    private final String hostname;
    private final String internalIpAddress;

    public EnvironmentUtils() {
        this.hostname = resolveHostname();
        this.internalIpAddress = resolveInternalIpAddress();
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Failed to get hostname, using 'unknown'", e);
            return "unknown";
        }
    }

    private String resolveInternalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("Failed to get internal IP address, using '127.0.0.1'", e);
            return "127.0.0.1";
        }
    }

}
