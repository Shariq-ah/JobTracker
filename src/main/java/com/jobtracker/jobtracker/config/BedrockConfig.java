package com.jobtracker.jobtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class BedrockConfig {

    @Value("${aws.bedrock.region}")
    private String region;

    @Value("${aws.bedrock.access.key}")
    private String accessKey;

    @Value("${aws.bedrock.secret.key}")
    private String secretKey;

    @Value("${ai.dev.mock.enabled:false}")
    private boolean devMockEnabled;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        AwsBasicCredentials credentials = resolveCredentials();

        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    private AwsBasicCredentials resolveCredentials() {
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            return AwsBasicCredentials.create(accessKey, secretKey);
        }
        if (devMockEnabled) {
            // Mock mode never calls Bedrock; placeholders satisfy AWS SDK non-blank requirement.
            return AwsBasicCredentials.create("unused-mock-access-key", "unused-mock-secret-key");
        }
        throw new IllegalStateException(
                "AWS credentials are required when ai.dev.mock.enabled=false. "
                        + "Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY (see .env.example).");
    }
}