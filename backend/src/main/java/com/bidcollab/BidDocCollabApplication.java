package com.bidcollab;

import com.bidcollab.agent.runtime.AgentRuntimeProperties;
import com.bidcollab.ai.AiProviderProperties;
import com.bidcollab.config.VectorStoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ AiProviderProperties.class, VectorStoreProperties.class,
    AgentRuntimeProperties.class })
public class BidDocCollabApplication {
  public static void main(String[] args) {
    SpringApplication.run(BidDocCollabApplication.class, args);
  }
}
