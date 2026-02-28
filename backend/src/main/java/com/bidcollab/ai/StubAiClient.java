package com.bidcollab.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.bidcollab.service.AiTokenUsageService;
import com.bidcollab.service.CurrentUserService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StubAiClient implements AiClient {
  // [AI-READ] 统一 AI 适配入口：按 provider 分发聊天与向量能力。
  private static final int FALLBACK_VECTOR_DIM = 1024;

  private final AiProviderProperties props;
  private final AiTokenUsageService tokenUsageService;
  private final CurrentUserService currentUserService;
  private final Map<AiProviderType, AtomicInteger> providerKeyCursor = new EnumMap<>(AiProviderType.class);

  public StubAiClient(AiProviderProperties props, AiTokenUsageService tokenUsageService,
      CurrentUserService currentUserService) {
    this.props = props;
    this.tokenUsageService = tokenUsageService;
    this.currentUserService = currentUserService;
    for (AiProviderType type : AiProviderType.values()) {
      providerKeyCursor.put(type, new AtomicInteger(0));
    }
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

  private record HttpErrorInfo(Integer httpStatus, String errorCode) {
  }

  // ── 按 provider 获取 OpenAI 兼容客户端 ────────────────

  private OpenAiCompatibleClient resolveOpenAiClient(AiProviderType provider) {
    return resolveOpenAiClient(provider, null);
  }

  private OpenAiCompatibleClient resolveOpenAiClient(AiProviderType provider, String apiKeyOverride) {
    return switch (provider) {
      case OPENAI -> new OpenAiCompatibleClient(
          props.getOpenAi().getBaseUrl(), apiKeyOverride == null ? props.getOpenAi().getApiKey() : apiKeyOverride);
      case ALIBABA -> new OpenAiCompatibleClient(
          props.getAlibaba().getBaseUrl(), apiKeyOverride == null ? props.getAlibaba().getApiKey() : apiKeyOverride);
      case SELF_HOSTED -> new OpenAiCompatibleClient(
          props.getSelfHosted().getBaseUrl(), apiKeyOverride == null ? props.getSelfHosted().getApiKey() : apiKeyOverride);
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
    // OPENAI / ALIBABA / SELF_HOSTED：多 key 轮询 + 顺序故障转移
    return withOpenAiCompatibleFailover(provider,
        client -> {
          OpenAiCompatibleClient.ChatResult res = client.chatWithUsage(model, systemPrompt, userPrompt);
          return new ChatDispatchResult(res.content(), res.promptTokens(),
              res.completionTokens(), res.totalTokens());
        });
  }

  // ── 向量分发 ────────────────────────────────────

  private EmbeddingDispatchResult dispatchEmbedding(AiProviderType provider, String model,
      String text) throws Exception {
    if (provider == AiProviderType.BAIDU) {
      BaiduAiClient baiduClient = new BaiduAiClient(props.getBaidu());
      BaiduAiClient.EmbeddingResult res = baiduClient.embeddingWithUsage(model, text);
      return new EmbeddingDispatchResult(res.vector(), res.promptTokens(), res.totalTokens());
    }
    return withOpenAiCompatibleFailover(provider,
        client -> {
          OpenAiCompatibleClient.EmbeddingResult res = client.embeddingWithUsage(model, text);
          return new EmbeddingDispatchResult(res.vector(), res.promptTokens(), res.totalTokens());
        });
  }

  private <T> T withOpenAiCompatibleFailover(
      AiProviderType provider,
      ThrowingClientFunction<T> operation) throws Exception {
    List<String> keys = resolveProviderKeyPool(provider);
    if (keys.isEmpty()) {
      return operation.apply(resolveOpenAiClient(provider));
    }
    int start = Math.floorMod(
        providerKeyCursor.get(provider).getAndIncrement(),
        keys.size());
    Exception lastEx = null;
    for (int offset = 0; offset < keys.size(); offset++) {
      String key = keys.get((start + offset) % keys.size());
      if (key == null || key.isBlank()) {
        continue;
      }
      try {
        return operation.apply(resolveOpenAiClient(provider, key));
      } catch (Exception ex) {
        lastEx = ex;
      }
    }
    if (lastEx != null) {
      throw lastEx;
    }
    throw new IllegalStateException("No available api key for provider: " + provider);
  }

  private List<String> resolveProviderKeyPool(AiProviderType provider) {
    List<String> keys = switch (provider) {
      case OPENAI -> props.getOpenAi().getApiKeys();
      case ALIBABA -> props.getAlibaba().getApiKeys();
      case SELF_HOSTED -> props.getSelfHosted().getApiKeys();
      // BAIDU 当前仍基于 apiKey + secretKey 配对认证，不在此列表分流
      default -> Collections.emptyList();
    };
    if (keys == null || keys.isEmpty()) {
      return Collections.emptyList();
    }
    return keys.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  @FunctionalInterface
  private interface ThrowingClientFunction<T> {
    T apply(OpenAiCompatibleClient client) throws Exception;
  }

  // ── Token 用量统一上报（消除 3 处 finally 块的重复） ──────

  private void recordUsage(String requestType, String scene, AiProviderType provider,
      String model, int promptTokens, int completionTokens, long latencyMs,
      boolean estimated, boolean success,
      String requestPayload, String responsePayload,
      Integer httpStatus, String errorCode, String errorMessage) {
    AiTraceContext.AiTraceMeta traceMeta = AiTraceContext.current();
    tokenUsageService.record(AiTokenUsageService.UsageRecordCommand.builder()
        .traceId(UUID.randomUUID().toString().replace("-", ""))
        .requestType(requestType)
        .provider(provider == null ? "UNKNOWN" : provider.name())
        .modelName(model)
        .scene(scene)
        .promptTokens(promptTokens)
        .completionTokens(completionTokens)
        .totalTokens(promptTokens + completionTokens)
        .latencyMs(latencyMs)
        .estimated(estimated)
        .success(success)
        .knowledgeBaseId(traceMeta == null ? null : traceMeta.knowledgeBaseId())
        .knowledgeDocumentId(traceMeta == null ? null : traceMeta.knowledgeDocumentId())
        .sectionId(traceMeta == null ? null : traceMeta.sectionId())
        .aiTaskId(traceMeta == null ? null : traceMeta.aiTaskId())
        .retryCount(traceMeta == null || traceMeta.retryCount() == null ? 0 : traceMeta.retryCount())
        .httpStatus(httpStatus)
        .errorCode(truncate(errorCode, 64))
        .errorMessage(truncate(errorMessage, 2000))
        .requestPayload(truncate(requestPayload, 8000))
        .responsePayload(truncate(responsePayload, 12000))
        .userId(currentUserService.getCurrentUserId())
        .build());
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
    String requestPayload = buildChatRequestPreview(systemPrompt, userPrompt);
    String responsePayload = null;
    Integer httpStatus = null;
    String errorCode = null;
    String errorMessage = null;
    try {
      if (provider == AiProviderType.MOCK) {
        // MOCK 模式：原样回显用户输入
        result = "[MOCK-CHAT]\n" + userPrompt;
        completionTokens = estimateTextTokens(result);
        responsePayload = truncate(result, 8000);
      } else {
        ChatDispatchResult res = dispatchChat(provider, model, systemPrompt, userPrompt);
        result = res.content();
        responsePayload = truncate(result, 8000);
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
      responsePayload = truncate(result, 8000);
      errorMessage = ex.getMessage();
      HttpErrorInfo info = parseHttpError(ex);
      httpStatus = info.httpStatus();
      errorCode = info.errorCode();
      return result;
    } finally {
      recordUsage("CHAT", "chat", provider, model, promptTokens, completionTokens,
          Duration.between(start, Instant.now()).toMillis(), estimated, success,
          requestPayload, responsePayload, httpStatus, errorCode, errorMessage);
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
    String requestPayload = buildEmbeddingRequestPreview(text);
    String responsePayload = null;
    Integer httpStatus = null;
    String errorCode = null;
    String errorMessage = null;
    try {
      if (provider == AiProviderType.MOCK) {
        List<Double> vector = fallbackEmbedding(text);
        responsePayload = "{\"vectorDim\":" + vector.size() + ",\"fallback\":true}";
        return vector;
      }
      EmbeddingDispatchResult res = dispatchEmbedding(provider, model, text);
      UsageTokens tokens = mergeUsage(res.promptTokens(), 0,
          res.totalTokens(), text, "", "");
      promptTokens = tokens.prompt();
      completionTokens = tokens.completion();
      estimated = tokens.estimated();
      responsePayload = "{\"vectorDim\":" + (res.vector() == null ? 0 : res.vector().size()) + "}";
      return res.vector();
    } catch (Exception ex) {
      success = false;
      errorMessage = ex.getMessage();
      HttpErrorInfo info = parseHttpError(ex);
      httpStatus = info.httpStatus();
      errorCode = info.errorCode();
      List<Double> vector = fallbackEmbedding(text);
      responsePayload = "{\"vectorDim\":" + vector.size() + ",\"fallback\":true}";
      return vector;
    } finally {
      recordUsage("EMBEDDING", "embedding", provider, model, promptTokens, completionTokens,
          Duration.between(start, Instant.now()).toMillis(), estimated, success,
          requestPayload, responsePayload, httpStatus, errorCode, errorMessage);
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
    String requestPayload = null;
    String responsePayload = null;
    Integer httpStatus = null;
    String errorCode = null;
    String errorMessage = null;
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
      requestPayload = buildChatRequestPreview(systemPrompt, input);
      promptTokens = estimateTextTokens(systemPrompt) + estimateTextTokens(input);

      if (provider == AiProviderType.MOCK) {
        completionTokens = estimateTextTokens("{}");
        responsePayload = "{}";
        return "{}";
      }
      ChatDispatchResult res = dispatchChat(provider, model, systemPrompt, input);
      String result = res.content();
      responsePayload = truncate(result, 8000);
      UsageTokens tokens = mergeUsage(res.promptTokens(), res.completionTokens(),
          res.totalTokens(), systemPrompt, input, result);
      promptTokens = tokens.prompt();
      completionTokens = tokens.completion();
      estimated = tokens.estimated();
      return result == null ? "{}" : result.trim();
    } catch (Exception ex) {
      success = false;
      errorMessage = ex.getMessage();
      HttpErrorInfo info = parseHttpError(ex);
      httpStatus = info.httpStatus();
      errorCode = info.errorCode();
      responsePayload = "{}";
      return "{}";
    } finally {
      recordUsage("KEYWORD_EXTRACT", "extractKeywordFrequency", provider, model,
          promptTokens, completionTokens,
          Duration.between(start, Instant.now()).toMillis(), estimated, success,
          requestPayload, responsePayload, httpStatus, errorCode, errorMessage);
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

  private HttpErrorInfo parseHttpError(Exception ex) {
    if (ex == null || ex.getMessage() == null) {
      return new HttpErrorInfo(null, null);
    }
    String msg = ex.getMessage();
    Matcher m = Pattern.compile("AI provider error:\\s*(\\d{3})").matcher(msg);
    Integer status = null;
    if (m.find()) {
      try {
        status = Integer.parseInt(m.group(1));
      } catch (NumberFormatException ignored) {
      }
    }
    String code = null;
    String lower = msg.toLowerCase(Locale.ROOT);
    if (lower.contains("rate limit")) {
      code = "RATE_LIMIT";
    } else if (lower.contains("unauthorized") || lower.contains("invalid api key")) {
      code = "UNAUTHORIZED";
    } else if (lower.contains("timeout")) {
      code = "TIMEOUT";
    }
    return new HttpErrorInfo(status, code);
  }

  private String buildChatRequestPreview(String systemPrompt, String userPrompt) {
    String sys = truncate(systemPrompt, 2000);
    String user = truncate(userPrompt, 4000);
    return "{\"systemPrompt\":" + quoteJson(sys) + ",\"userPrompt\":" + quoteJson(user) + "}";
  }

  private String buildEmbeddingRequestPreview(String text) {
    String safe = truncate(text, 4000);
    return "{\"text\":" + quoteJson(safe) + "}";
  }

  private String quoteJson(String text) {
    if (text == null) {
      return "null";
    }
    return "\"" + text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t") + "\"";
  }

  private String truncate(String text, int maxLen) {
    if (text == null) {
      return null;
    }
    if (text.length() <= maxLen) {
      return text;
    }
    return text.substring(0, maxLen);
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
