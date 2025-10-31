package edu.northeastern.hanafeng.chatsystem.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@Slf4j
public class AwsConfig {

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        log.info("Creating shared AWS credentials provider");
        DefaultCredentialsProvider provider = DefaultCredentialsProvider.create();
        // Force eager initialization by resolving credentials now
        provider.resolveCredentials();
        log.info("AWS credentials provider initialized successfully");
        return provider;
    }

    @Bean
    public Region awsRegion() {
        Region region = new DefaultAwsRegionProviderChain().getRegion();
        log.info("Using AWS region: {}", region);
        return region;
    }

    @Bean
    public SqsClient sqsClient(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating SqsClient bean");
        return SqsClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .build();
    }

    @Bean
    public CloudWatchClient cloudWatchClient(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating CloudWatchClient bean");
        return CloudWatchClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient(AwsCredentialsProvider credentialsProvider, Region region) {
        log.info("Creating DynamoDbClient bean");
        return DynamoDbClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .build();
    }
}
