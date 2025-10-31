package edu.northeastern.hanafeng.chatsystem.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
    org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration.class,
    org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration.class
})
@ComponentScan(basePackages = {
    "edu.northeastern.hanafeng.chatsystem.client",
    "edu.northeastern.hanafeng.chatsystem.common"
})
@EnableScheduling
public class ChatClientApplication {
    
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ChatClientApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.setAdditionalProfiles("client");
        app.run(args);
    }
}