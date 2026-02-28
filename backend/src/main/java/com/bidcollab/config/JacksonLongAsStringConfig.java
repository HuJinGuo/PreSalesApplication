package com.bidcollab.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

/**
 * 前端使用 JS Number 无法安全承载 Snowflake Long（超过 2^53-1），
 * 统一将 Long 序列化为字符串，避免 ID 精度丢失导致 404 / 查询不到。
 */
@Configuration
public class JacksonLongAsStringConfig {

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer longAsStringCustomizer() {
    return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance)
        .serializerByType(Long.TYPE, ToStringSerializer.instance);
  }
}
