package edu.northeastern.hanafeng.chatsystem.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("client")  // ← 只在 client profile 下生效
public class ClientBeanConfig {

    /**
     * ObjectMapper for JSON serialization/deserialization
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.findAndRegisterModules();
        return mapper;
    }
}