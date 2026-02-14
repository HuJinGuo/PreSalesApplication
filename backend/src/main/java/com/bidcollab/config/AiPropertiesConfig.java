package com.bidcollab.config;

import com.bidcollab.ai.AiProviderProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiPropertiesConfig {
}
