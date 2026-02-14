package com.bidcollab.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
  private final String uploadDir;

  public StaticResourceConfig(@Value("${app.upload.storage-dir:/tmp/bid-doc-uploads}") String uploadDir) {
    this.uploadDir = uploadDir;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String location = "file:" + uploadDir + "/";
    registry.addResourceHandler("/files/**").addResourceLocations(location);
  }
}
