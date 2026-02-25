package com.bidcollab.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
      switch (provider) {
        case OPENAI:
          OpenAiCompatibleClient openAiClient = new OpenAiCompatibleClient(
              props.getOpenAi().getBaseUrl(),
              props.getOpenAi().getApiKey());
          OpenAiCompatibleClient.ChatResult openAiRes = openAiClient.chatWithUsage(model, systemPrompt, userPrompt);
          result = openAiRes.content();
          UsageTokens tokensA = mergeUsage(openAiRes.promptTokens(), openAiRes.completionTokens(),
              openAiRes.totalTokens(), systemPrompt, userPrompt, result);
          promptTokens = tokensA.prompt();
          completionTokens = tokensA.completion();
          estimated = tokensA.estimated();
          break;
        case ALIBABA:
          OpenAiCompatibleClient aliClient = new OpenAiCompatibleClient(
              props.getAlibaba().getBaseUrl(), props.getAlibaba().getApiKey());
          OpenAiCompatibleClient.ChatResult aliRes = aliClient.chatWithUsage(model, systemPrompt, userPrompt);
          result = aliRes.content();
          UsageTokens tokensAli = mergeUsage(aliRes.promptTokens(), aliRes.completionTokens(),
              aliRes.totalTokens(), systemPrompt, userPrompt, result);
          promptTokens = tokensAli.prompt();
          completionTokens = tokensAli.completion();
          estimated = tokensAli.estimated();
          break;
        case SELF_HOSTED:
          OpenAiCompatibleClient selfClient = new OpenAiCompatibleClient(
              props.getSelfHosted().getBaseUrl(), props.getSelfHosted().getApiKey());
          OpenAiCompatibleClient.ChatResult selfRes = selfClient.chatWithUsage(model, systemPrompt, userPrompt);
          result = selfRes.content();
          UsageTokens tokensSelf = mergeUsage(selfRes.promptTokens(), selfRes.completionTokens(),
              selfRes.totalTokens(), systemPrompt, userPrompt, result);
          promptTokens = tokensSelf.prompt();
          completionTokens = tokensSelf.completion();
          estimated = tokensSelf.estimated();
          break;
        case BAIDU:
          BaiduAiClient baiduClient = new BaiduAiClient(props.getBaidu());
          BaiduAiClient.ChatResult bdRes = baiduClient.chatWithUsage(model, systemPrompt, userPrompt);
          result = bdRes.content();
          UsageTokens tokensBd = mergeUsage(bdRes.promptTokens(), bdRes.completionTokens(),
              bdRes.totalTokens(), systemPrompt, userPrompt, result);
          promptTokens = tokensBd.prompt();
          completionTokens = tokensBd.completion();
          estimated = tokensBd.estimated();
          break;
        case MOCK:
        default:
          result = "[MOCK-CHAT]\n" + userPrompt;
          completionTokens = estimateTextTokens(result);
          break;
      }
      return result;
    } catch (Exception ex) {
      success = false;
      result = "[AI调用失败，使用兜底结果]\n" + userPrompt;
      completionTokens = estimateTextTokens(result);
      return result;
    } finally {
      long latency = Duration.between(start, Instant.now()).toMillis();
      tokenUsageService.record(
          "CHAT",
          provider.name(),
          model,
          "chat",
          promptTokens,
          completionTokens,
          promptTokens + completionTokens,
          latency,
          estimated,
          success,
          currentUserService.getCurrentUserId());
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
      List<Double> result;
      switch (provider) {
        case OPENAI:
          OpenAiCompatibleClient openAiClient = new OpenAiCompatibleClient(
              props.getOpenAi().getBaseUrl(),
              props.getOpenAi().getApiKey());
          OpenAiCompatibleClient.EmbeddingResult oa = openAiClient.embeddingWithUsage(model, text);
          result = oa.vector();
          UsageTokens tOa = mergeUsage(oa.promptTokens(), 0, oa.totalTokens(), text, "", "");
          promptTokens = tOa.prompt();
          completionTokens = tOa.completion();
          estimated = tOa.estimated();
          break;
        case ALIBABA:
          OpenAiCompatibleClient aliClient = new OpenAiCompatibleClient(
              props.getAlibaba().getBaseUrl(), props.getAlibaba().getApiKey());
          OpenAiCompatibleClient.EmbeddingResult ali = aliClient.embeddingWithUsage(model, text);
          result = ali.vector();
          UsageTokens tAli = mergeUsage(ali.promptTokens(), 0, ali.totalTokens(), text, "", "");
          promptTokens = tAli.prompt();
          completionTokens = tAli.completion();
          estimated = tAli.estimated();
          break;
        case SELF_HOSTED:
          OpenAiCompatibleClient selfClient = new OpenAiCompatibleClient(
              props.getSelfHosted().getBaseUrl(), props.getSelfHosted().getApiKey());
          OpenAiCompatibleClient.EmbeddingResult self = selfClient.embeddingWithUsage(model, text);
          result = self.vector();
          UsageTokens tSelf = mergeUsage(self.promptTokens(), 0, self.totalTokens(), text, "", "");
          promptTokens = tSelf.prompt();
          completionTokens = tSelf.completion();
          estimated = tSelf.estimated();
          break;
        case BAIDU:
          BaiduAiClient baiduClient = new BaiduAiClient(props.getBaidu());
          BaiduAiClient.EmbeddingResult bd = baiduClient.embeddingWithUsage(model, text);
          result = bd.vector();
          UsageTokens tBd = mergeUsage(bd.promptTokens(), 0, bd.totalTokens(), text, "", "");
          promptTokens = tBd.prompt();
          completionTokens = tBd.completion();
          estimated = tBd.estimated();
          break;
        case MOCK:
        default:
          result = fallbackEmbedding(text);
          break;
      }
      return result;
    } catch (Exception ex) {
      success = false;
      return fallbackEmbedding(text);
    } finally {
      long latency = Duration.between(start, Instant.now()).toMillis();
      tokenUsageService.record(
          "EMBEDDING",
          provider.name(),
          model,
          "embedding",
          promptTokens,
          completionTokens,
          promptTokens + completionTokens,
          latency,
          estimated,
          success,
          currentUserService.getCurrentUserId());
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
      promptTokens = estimateTextTokens(systemPrompt) + estimateTextTokens(input);
      switch (provider) {
        case OPENAI:
          OpenAiCompatibleClient openAiClient = new OpenAiCompatibleClient(
              props.getOpenAi().getBaseUrl(),
              props.getOpenAi().getApiKey());
          OpenAiCompatibleClient.ChatResult oa = openAiClient.chatWithUsage(model, systemPrompt, input);
          result = oa.content();
          UsageTokens tokOa = mergeUsage(oa.promptTokens(), oa.completionTokens(), oa.totalTokens(), systemPrompt,
              input, result);
          promptTokens = tokOa.prompt();
          completionTokens = tokOa.completion();
          estimated = tokOa.estimated();
          break;
        case ALIBABA:
          OpenAiCompatibleClient aliClient = new OpenAiCompatibleClient(
              props.getAlibaba().getBaseUrl(), props.getAlibaba().getApiKey());
          OpenAiCompatibleClient.ChatResult ali = aliClient.chatWithUsage(model, systemPrompt, input);
          result = ali.content();
          UsageTokens tokAli = mergeUsage(ali.promptTokens(), ali.completionTokens(), ali.totalTokens(), systemPrompt,
              input, result);
          promptTokens = tokAli.prompt();
          completionTokens = tokAli.completion();
          estimated = tokAli.estimated();
          break;
        case SELF_HOSTED:
          OpenAiCompatibleClient selfClient = new OpenAiCompatibleClient(
              props.getSelfHosted().getBaseUrl(), props.getSelfHosted().getApiKey());
          OpenAiCompatibleClient.ChatResult self = selfClient.chatWithUsage(model, systemPrompt, input);
          result = self.content();
          UsageTokens tokSelf = mergeUsage(self.promptTokens(), self.completionTokens(), self.totalTokens(),
              systemPrompt, input, result);
          promptTokens = tokSelf.prompt();
          completionTokens = tokSelf.completion();
          estimated = tokSelf.estimated();
          break;
        case BAIDU:
          BaiduAiClient baiduClient = new BaiduAiClient(props.getBaidu());
          BaiduAiClient.ChatResult bd = baiduClient.chatWithUsage(model, systemPrompt, input);
          result = bd.content();
          UsageTokens tokBd = mergeUsage(bd.promptTokens(), bd.completionTokens(), bd.totalTokens(), systemPrompt,
              input, result);
          promptTokens = tokBd.prompt();
          completionTokens = tokBd.completion();
          estimated = tokBd.estimated();
          break;
        case MOCK:
        default:
          result = "{}";
          completionTokens = estimateTextTokens(result);
          break;
      }
      return result == null ? "{}" : result.trim();
    } catch (Exception ex) {
      success = false;
      return "{}";
    } finally {
      long latency = Duration.between(start, Instant.now()).toMillis();
      tokenUsageService.record(
          "KEYWORD_EXTRACT",
          provider.name(),
          model,
          "extractKeywordFrequency",
          promptTokens,
          completionTokens,
          promptTokens + completionTokens,
          latency,
          estimated,
          success,
          currentUserService.getCurrentUserId());
    }
  }

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
