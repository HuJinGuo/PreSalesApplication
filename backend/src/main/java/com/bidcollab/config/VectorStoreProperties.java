package com.bidcollab.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.vector")
public class VectorStoreProperties {
  private String provider = "milvus";
  private Milvus milvus = new Milvus();

  @Data
  public static class Milvus {
    private String host = "127.0.0.1";
    private int port = 19530;
    private String collection = "knowledge_chunk_vector";
    private int embeddingDim = 1024;
    private int nprobe = 16;
    private int shardsNum = 2;
  }
}
