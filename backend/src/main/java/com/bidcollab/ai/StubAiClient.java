package com.bidcollab.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StubAiClient implements AiClient {
  // [AI-READ] 统一 AI 适配入口：按 provider 分发聊天与向量能力。
  private static final int FALLBACK_VECTOR_DIM = 256;

  private final AiProviderProperties props;

  public StubAiClient(AiProviderProperties props) {
    this.props = props;
  }

  @Override
  public String chat(String systemPrompt, String userPrompt) {
    // [AI-READ] 对话调用：失败时降级为可读兜底内容，避免阻塞主流程。
    try {
      String result;
      switch (resolveChatProvider()) {
        case OPENAI:
          OpenAiCompatibleClient openAiClient = new OpenAiCompatibleClient(
              props.getOpenAi().getBaseUrl(),
              props.getOpenAi().getApiKey());
          result = openAiClient.chat(props.getChatModel(), systemPrompt, userPrompt);
          break;
        case ALIBABA:
          OpenAiCompatibleClient aliClient = new OpenAiCompatibleClient(
              props.getAlibaba().getBaseUrl(), props.getAlibaba().getApiKey());
          result = aliClient.chat(props.getChatModel(), systemPrompt, userPrompt);
          break;
        case SELF_HOSTED:
          OpenAiCompatibleClient selfClient = new OpenAiCompatibleClient(
              props.getSelfHosted().getBaseUrl(), props.getSelfHosted().getApiKey());
          result = selfClient.chat(props.getChatModel(), systemPrompt, userPrompt);
          break;
        case BAIDU:
          BaiduAiClient baiduClient = new BaiduAiClient(props.getBaidu());
          result = baiduClient.chat(props.getChatModel(), systemPrompt, userPrompt);
          break;
        case MOCK:
        default:
          result = "[MOCK-CHAT]\n" + userPrompt;
          break;
      }
      return result;
    } catch (Exception ex) {
      return "[AI调用失败，使用兜底结果]\n" + userPrompt;
    }
  }

  @Override
  public List<Double> embedding(String text) {
    // [AI-READ] 向量调用：失败时回退哈希向量，保障检索流程可运行。
    try {
      List<Double> result;
      switch (resolveEmbeddingProvider()) {
        case OPENAI:
          OpenAiCompatibleClient openAiClient = new OpenAiCompatibleClient(
              props.getOpenAi().getBaseUrl(),
              props.getOpenAi().getApiKey());
          result = openAiClient.embedding(props.getEmbeddingModel(), text);
          break;
        case ALIBABA:
          OpenAiCompatibleClient aliClient = new OpenAiCompatibleClient(
              props.getAlibaba().getBaseUrl(), props.getAlibaba().getApiKey());
          result = aliClient.embedding(props.getEmbeddingModel(), text);
          break;
        case SELF_HOSTED:
          OpenAiCompatibleClient selfClient = new OpenAiCompatibleClient(
              props.getSelfHosted().getBaseUrl(), props.getSelfHosted().getApiKey());
          result = selfClient.embedding(props.getEmbeddingModel(), text);
          break;
        case BAIDU:
          BaiduAiClient baiduClient = new BaiduAiClient(props.getBaidu());
          result = baiduClient.embedding(props.getEmbeddingModel(), text);
          break;
        case MOCK:
        default:
          result = fallbackEmbedding(text);
          break;
      }
      return result;
    } catch (Exception ex) {
      return fallbackEmbedding(text);
    }
  }

  @Override
  public String extractKeywordFrequency(String text) {
    try {
      String result;
      String systemPrompt = """
          你是一个专业的行业文档关键词提取助手。
          请从输入文本中提取“可用于检索/图谱”的关键词，并统计每个关键词在文本中出现的大致频次。
          仅输出 JSON 对象，不要输出任何解释文字，不要使用 markdown 代码块。
          JSON 结构如下：
          {
            "keyword1": frequency1,
            "keyword2": frequency2,
            ...
          }
          约束：
          1) frequency 必须是正整数；
          2) 关键词不要超过 20 个；
          3) 去掉“系统/方案/进行/以及/说明/如下”等无意义词；
          4) 如果没有有效关键词，返回 {}。
          """;
      String input = text == null ? "" : text.trim();
      if (input.length() > 4000) {
        input = input.substring(0, 4000);
      }
      switch (resolveChatProvider()) {
        case OPENAI:
          OpenAiCompatibleClient openAiClient = new OpenAiCompatibleClient(
              props.getOpenAi().getBaseUrl(),
              props.getOpenAi().getApiKey());
          result = openAiClient.chat(props.getChatModel(), systemPrompt, input);
          break;
        case ALIBABA:
          OpenAiCompatibleClient aliClient = new OpenAiCompatibleClient(
              props.getAlibaba().getBaseUrl(), props.getAlibaba().getApiKey());
          result = aliClient.chat(props.getChatModel(), systemPrompt, input);
          break;
        case SELF_HOSTED:
          OpenAiCompatibleClient selfClient = new OpenAiCompatibleClient(
              props.getSelfHosted().getBaseUrl(), props.getSelfHosted().getApiKey());
          result = selfClient.chat(props.getChatModel(), systemPrompt, input);
          break;
        case BAIDU:
          BaiduAiClient baiduClient = new BaiduAiClient(props.getBaidu());
          result = baiduClient.chat(props.getChatModel(), systemPrompt, input);
          break;
        case MOCK:
        default:
          result = "{}";
          break;
      }
      return result == null ? "{}" : result.trim();
    } catch (Exception ex) {
      return "{}";
    }
  }

  private List<Double> fallbackEmbedding(String text) {
    byte[] input = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
    byte[] digest;
    try {
      digest = MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      digest = new byte[32];
    }
    List<Double> vector = new ArrayList<>(FALLBACK_VECTOR_DIM);
    for (int i = 0; i < FALLBACK_VECTOR_DIM; i++) {
      int v = digest[i % digest.length] & 0xFF;
      vector.add((v / 255.0) * 2 - 1);
    }
    return vector;
  }

  private AiProviderType resolveChatProvider() {
    return props.getChatProvider() == null ? props.getProvider() : props.getChatProvider();
  }

  private AiProviderType resolveEmbeddingProvider() {
    return props.getEmbeddingProvider() == null ? props.getProvider() : props.getEmbeddingProvider();
  }

}
