package com.bidcollab.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class OpenAiCompatibleClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final String baseUrl;
  private final String apiKey;

  record ChatResult(String content, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
  }

  record EmbeddingResult(List<Double> vector, Integer promptTokens, Integer totalTokens) {
  }

  OpenAiCompatibleClient(String baseUrl, String apiKey) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
  }

  String chat(String model, String systemPrompt, String userPrompt) throws IOException, InterruptedException {
    return chatWithUsage(model, systemPrompt, userPrompt).content();
  }

  ChatResult chatWithUsage(String model, String systemPrompt, String userPrompt) throws IOException, InterruptedException {
    Map<String, Object> body = Map.of(
        "model", model,
        "messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        )
    );
    String result = postJson(baseUrl + "/chat/completions", body);
    JsonNode root = MAPPER.readTree(result);
    JsonNode usage = root.path("usage");
    return new ChatResult(
        root.path("choices").path(0).path("message").path("content").asText(),
        usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt(),
        usage.path("completion_tokens").isMissingNode() ? null : usage.path("completion_tokens").asInt(),
        usage.path("total_tokens").isMissingNode() ? null : usage.path("total_tokens").asInt());
  }

  List<Double> embedding(String model, String text) throws IOException, InterruptedException {
    return embeddingWithUsage(model, text).vector();
  }

  EmbeddingResult embeddingWithUsage(String model, String text) throws IOException, InterruptedException {
    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("model", model);
    // Nvidia Integrate 对 embeddings 更稳定的写法是 input 为数组。
    body.put("input", List.of(text));
    // 仅在 Nvidia 场景附加特有参数，避免影响其它 OpenAI-compatible 供应商。
    if (isNvidiaEmbedding(model)) {
      body.put("input_type", "passage");
      body.put("encoding_format", "float");
      body.put("truncate", "NONE");
    }
    String result = postJson(baseUrl + "/embeddings", body);
    JsonNode root = MAPPER.readTree(result);
    JsonNode arr = root.path("data").path(0).path("embedding");
    List<Double> vec = new ArrayList<>();
    for (JsonNode n : arr) {
      vec.add(n.asDouble());
    }
    JsonNode usage = root.path("usage");
    return new EmbeddingResult(
        vec,
        usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt(),
        usage.path("total_tokens").isMissingNode() ? null : usage.path("total_tokens").asInt());
  }

  private boolean isNvidiaEmbedding(String model) {
    String m = model == null ? "" : model.toLowerCase();
    String u = baseUrl == null ? "" : baseUrl.toLowerCase();
    return m.startsWith("nvidia/") || u.contains("integrate.api.nvidia.com");
  }

  private String postJson(String url, Map<String, Object> body) throws IOException, InterruptedException {
    String payload = MAPPER.writeValueAsString(body);
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(60))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload));
    if (apiKey != null && !apiKey.isBlank()) {
      builder.header("Authorization", "Bearer " + apiKey);
    }
    HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException("AI provider error: " + response.statusCode() + " " + response.body());
    }
    return response.body();
  }
}
