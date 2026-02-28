package com.bidcollab.ai;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.ai")
public class AiProviderProperties {
  private AiProviderType provider = AiProviderType.MOCK;
  // Optional split providers. If null, fallback to `provider`.
  private AiProviderType chatProvider;
  private AiProviderType embeddingProvider;
  private String chatModel = "gpt-4o-mini";
  private String embeddingModel = "text-embedding-3-small";
  private String keywordModel = "glm-5";

  private OpenAi openAi = new OpenAi();
  private Alibaba alibaba = new Alibaba();
  private Baidu baidu = new Baidu();
  private SelfHosted selfHosted = new SelfHosted();

  @Data
  public static class OpenAi {
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey;
    /**
     * 多 key 轮询池（可选）。配置后优先使用该列表；
     * 未配置时回退到 apiKey。
     */
    private List<String> apiKeys = new ArrayList<>();
  }

  @Data
  public static class Alibaba {
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey;
    private List<String> apiKeys = new ArrayList<>();
  }

  @Data
  public static class Baidu {
    private String authUrl = "https://aip.baidubce.com/oauth/2.0/token";
    private String chatUrl = "https://qianfan.baidubce.com/v2/chat/completions";
    private String embeddingUrl = "https://qianfan.baidubce.com/v2/embeddings";
    private String apiKey;
    private String secretKey;
    private List<String> apiKeys = new ArrayList<>();
  }

  @Data
  public static class SelfHosted {
    private String baseUrl = "http://localhost:8000/v1";
    private String apiKey;
    private List<String> apiKeys = new ArrayList<>();
  }
}
