package com.bidcollab.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.bidcollab.service.AiTokenUsageService;
import com.bidcollab.service.CurrentUserService;
import org.springframework.stereotype.Component;

@Component
public class StubAiClient implements AiClient {
  // [AI-READ] 统一 AI 适配入口：按 provider 分发聊天与向量能力。
  private static final int FALLBACK_VECTOR_DIM = 1024;

  private final AiProviderProperties props;
  private final AiTokenUsageService tokenUsageService;
  private final CurrentUserService currentUserService;

  public StubAiClient(AiProviderProperties props, AiTokenUsageService tokenUsageService,
      CurrentUserService currentUserService) {
    this.props = props;
    this.tokenUsageService = tokenUsageService;
    this.currentUserService = currentUserService;
  }

  // ── 统一分发结果 ────────────────────────────────────

  /** 聊天分发结果（统一 OpenAI / Baidu 等不同 SDK 的返回） */
  private record ChatDispatchResult(String content, Integer promptTokens,
      Integer completionTokens, Integer totalTokens) {
  }

  /** 向量分发结果 */
  private record EmbeddingDispatchResult(List<Double> vector, Integer promptTokens,
      Integer totalTokens) {
  }

  // ── 按 provider 获取 OpenAI 兼容客户端 ────────────────

  private OpenAiCompatibleClient resolveOpenAiClient(AiProviderType provider) {
    return switch (provider) {
      case OPENAI -> new OpenAiCompatibleClient(
          props.getOpenAi().getBaseUrl(), props.getOpenAi().getApiKey());
      case ALIBABA -> new OpenAiCompatibleClient(
          props.getAlibaba().getBaseUrl(), props.getAlibaba().getApiKey());
      case SELF_HOSTED -> new OpenAiCompatibleClient(
          props.getSelfHosted().getBaseUrl(), props.getSelfHosted().getApiKey());
      default -> throw new IllegalArgumentException("Provider " + provider + " 不支持 OpenAI 兼容协议");
    };
  }

  // ── 聊天分发（消除 OPENAI/ALIBABA/SELF_HOSTED/BAIDU 四分支重复） ──

  private ChatDispatchResult dispatchChat(AiProviderType provider, String model,
      String systemPrompt, String userPrompt) throws Exception {
    if (provider == AiProviderType.BAIDU) {
      BaiduAiClient baiduClient = new BaiduAiClient(props.getBaidu());
      BaiduAiClient.ChatResult res = baiduClient.chatWithUsage(model, systemPrompt, userPrompt);
      return new ChatDispatchResult(res.content(), res.promptTokens(),
          res.completionTokens(), res.totalTokens());
    }
    // OPENAI / ALIBABA / SELF_HOSTED 均走 OpenAI 兼容协议
    OpenAiCompatibleClient client = resolveOpenAiClient(provider);
    OpenAiCompatibleClient.ChatResult res = client.chatWithUsage(model, systemPrompt, userPrompt);
    return new ChatDispatchResult(res.content(), res.promptTokens(),
        res.completionTokens(), res.totalTokens());
  }

  // ── 向量分发 ────────────────────────────────────

  private EmbeddingDispatchResult dispatchEmbedding(AiProviderType provider, String model,
      String text) throws Exception {
    if (provider == AiProviderType.BAIDU) {
      BaiduAiClient baiduClient = new BaiduAiClient(props.getBaidu());
      BaiduAiClient.EmbeddingResult res = baiduClient.embeddingWithUsage(model, text);
      return new EmbeddingDispatchResult(res.vector(), res.promptTokens(), res.totalTokens());
    }
    OpenAiCompatibleClient client = resolveOpenAiClient(provider);
    OpenAiCompatibleClient.EmbeddingResult res = client.embeddingWithUsage(model, text);
    return new EmbeddingDispatchResult(res.vector(), res.promptTokens(), res.totalTokens());
  }

  // ── Token 用量统一上报（消除 3 处 finally 块的重复） ──────

  private void recordUsage(String requestType, String scene, AiProviderType provider,
      String model, int promptTokens, int completionTokens, long latencyMs,
      boolean estimated, boolean success) {
    tokenUsageService.record(
        requestType, provider.name(), model, scene,
        promptTokens, completionTokens, promptTokens + completionTokens,
        latencyMs, estimated, success,
        currentUserService.getCurrentUserId());
  }

  // ══════════════════════════════════════════════════
  // 公开接口
  // ══════════════════════════════════════════════════

  @Override
  public String chat(String systemPrompt, String userPrompt) {
    // [AI-READ] 对话调用：失败时降级为可读兜底内容，避免阻塞主流程。
    Instant start = Instant.now();
    AiProviderType provider = resolveChatProvider();
    String model = props.getChatModel();
    String result;
    boolean success = true;
    int promptTokens = estimateTextTokens(systemPrompt) + estimateTextTokens(userPrompt);
    int completionTokens = 0;
    boolean estimated = true;
    try {
      if (provider == AiProviderType.MOCK) {
        // MOCK 模式：原样回显用户输入
        result = "[MOCK-CHAT]\n" + userPrompt;
        completionTokens = estimateTextTokens(result);
      } else {
        ChatDispatchResult res = dispatchChat(provider, model, systemPrompt, userPrompt);
        result = res.content();
        UsageTokens tokens = mergeUsage(res.promptTokens(), res.completionTokens(),
            res.totalTokens(), systemPrompt, userPrompt, result);
        promptTokens = tokens.prompt();
        completionTokens = tokens.completion();
        estimated = tokens.estimated();
      }
      return result;
    } catch (Exception ex) {
      success = false;
      result = "[AI调用失败，使用兜底结果]\n" + userPrompt;
      completionTokens = estimateTextTokens(result);
      return result;
    } finally {
      recordUsage("CHAT", "chat", provider, model, promptTokens, completionTokens,
          Duration.between(start, Instant.now()).toMillis(), estimated, success);
    }
  }

  @Override
  public List<Double> embedding(String text) {
    // [AI-READ] 向量调用：失败时回退哈希向量，保障检索流程可运行。
    Instant start = Instant.now();
    AiProviderType provider = resolveEmbeddingProvider();
    String model = props.getEmbeddingModel();
    int promptTokens = estimateTextTokens(text);
    int completionTokens = 0;
    boolean success = true;
    boolean estimated = true;
    try {
      if (provider == AiProviderType.MOCK) {
        return fallbackEmbedding(text);
      }
      EmbeddingDispatchResult res = dispatchEmbedding(provider, model, text);
      UsageTokens tokens = mergeUsage(res.promptTokens(), 0,
          res.totalTokens(), text, "", "");
      promptTokens = tokens.prompt();
      completionTokens = tokens.completion();
      estimated = tokens.estimated();
      return res.vector();
    } catch (Exception ex) {
      success = false;
      return fallbackEmbedding(text);
    } finally {
      recordUsage("EMBEDDING", "embedding", provider, model, promptTokens, completionTokens,
          Duration.between(start, Instant.now()).toMillis(), estimated, success);
    }
  }

  @Override
  public String extractKeywordFrequency(String text) {
    Instant start = Instant.now();
    AiProviderType provider = resolveChatProvider();
    String model = props.getChatModel();
    int promptTokens = 0;
    int completionTokens = 0;
    boolean success = true;
    boolean estimated = true;
    try {
      String systemPrompt = """
          你是一个专业的行业文档关键词提取助手。
          请从输入文本中提取"可用于检索/图谱"的关键词，并统计每个关键词在文本中出现的大致频次。
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
          3) 去掉"系统/方案/进行/以及/说明/如下"等无意义词；
          4) 如果没有有效关键词，返回 {}。
          """;
      String input = text == null ? "" : text.trim();
      if (input.length() > 4000) {
        input = input.substring(0, 4000);
      }
      promptTokens = estimateTextTokens(systemPrompt) + estimateTextTokens(input);

      if (provider == AiProviderType.MOCK) {
        completionTokens = estimateTextTokens("{}");
        return "{}";
      }
      ChatDispatchResult res = dispatchChat(provider, model, systemPrompt, input);
      String result = res.content();
      UsageTokens tokens = mergeUsage(res.promptTokens(), res.completionTokens(),
          res.totalTokens(), systemPrompt, input, result);
      promptTokens = tokens.prompt();
      completionTokens = tokens.completion();
      estimated = tokens.estimated();
      return result == null ? "{}" : result.trim();
    } catch (Exception ex) {
      success = false;
      return "{}";
    } finally {
      recordUsage("KEYWORD_EXTRACT", "extractKeywordFrequency", provider, model,
          promptTokens, completionTokens,
          Duration.between(start, Instant.now()).toMillis(), estimated, success);
    }
  }

  // ══════════════════════════════════════════════════
  // 内部工具方法
  // ══════════════════════════════════════════════════

  private UsageTokens mergeUsage(Integer promptTokensRaw,
      Integer completionTokensRaw,
      Integer totalTokensRaw,
      String promptA,
      String promptB,
      String completion) {
    Integer prompt = promptTokensRaw;
    Integer completionTokens = completionTokensRaw;
    Integer total = totalTokensRaw;
    boolean estimated = false;
    if (prompt == null || prompt < 0) {
      prompt = estimateTextTokens(promptA) + estimateTextTokens(promptB);
      estimated = true;
    }
    if (completionTokens == null || completionTokens < 0) {
      completionTokens = estimateTextTokens(completion);
      estimated = true;
    }
    if (total == null || total < 0) {
      total = prompt + completionTokens;
      estimated = true;
    }
    if (completionTokens == 0 && total > prompt) {
      completionTokens = total - prompt;
    }
    return new UsageTokens(prompt, completionTokens, total, estimated);
  }

  private int estimateTextTokens(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    int len = text.trim().length();
    return Math.max(1, (int) Math.ceil(len / 1.8d));
  }

  private record UsageTokens(int prompt, int completion, int total, boolean estimated) {
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
