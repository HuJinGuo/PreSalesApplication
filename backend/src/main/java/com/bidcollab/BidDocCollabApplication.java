package com.bidcollab;

import com.bidcollab.ai.AiProviderProperties;
import com.bidcollab.config.VectorStoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// 集中启用所有 @ConfigurationProperties 类，不需要为每个 Properties 单独建 Config 文件
@SpringBootApplication
@EnableConfigurationProperties({ AiProviderProperties.class, VectorStoreProperties.class })
public class BidDocCollabApplication {
  public static void main(String[] args) {
    SpringApplication.run(BidDocCollabApplication.class, args);
  }
}
