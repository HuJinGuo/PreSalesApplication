package com.bidcollab.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class BaiduAiClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final AiProviderProperties.Baidu conf;

  private String accessToken;
  private Instant expireAt = Instant.EPOCH;

  record ChatResult(String content, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
  }

  record EmbeddingResult(List<Double> vector, Integer promptTokens, Integer totalTokens) {
  }

  BaiduAiClient(AiProviderProperties.Baidu conf) {
    this.conf = conf;
  }

  synchronized String token() throws IOException, InterruptedException {
    if (accessToken != null && Instant.now().isBefore(expireAt.minusSeconds(300))) {
      return accessToken;
    }
    String url = conf.getAuthUrl()
        + "?grant_type=client_credentials&client_id=" + encode(conf.getApiKey())
        + "&client_secret=" + encode(conf.getSecretKey());
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException("Baidu auth failed: " + response.statusCode() + " " + response.body());
    }
    JsonNode root = MAPPER.readTree(response.body());
    accessToken = root.path("access_token").asText();
    long expiresIn = root.path("expires_in").asLong(3600);
    expireAt = Instant.now().plusSeconds(expiresIn);
    return accessToken;
  }

  String chat(String model, String systemPrompt, String userPrompt) throws IOException, InterruptedException {
    return chatWithUsage(model, systemPrompt, userPrompt).content();
  }

  ChatResult chatWithUsage(String model, String systemPrompt, String userPrompt) throws IOException, InterruptedException {
    String payload = MAPPER.writeValueAsString(Map.of(
        "model", model,
        "messages", List.of(
            Map.of("role", "user", "content", systemPrompt + "\n\n" + userPrompt)
        )
    ));
    String result = post(conf.getChatUrl(), payload);
    JsonNode root = MAPPER.readTree(result);
    JsonNode usage = root.path("usage");
    return new ChatResult(
        root.path("result").asText(root.path("choices").path(0).path("message").path("content").asText()),
        usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt(),
        usage.path("completion_tokens").isMissingNode() ? null : usage.path("completion_tokens").asInt(),
        usage.path("total_tokens").isMissingNode() ? null : usage.path("total_tokens").asInt());
  }

  List<Double> embedding(String model, String text) throws IOException, InterruptedException {
    return embeddingWithUsage(model, text).vector();
  }

  EmbeddingResult embeddingWithUsage(String model, String text) throws IOException, InterruptedException {
    String payload = MAPPER.writeValueAsString(Map.of(
        "model", model,
        "input", List.of(text)
    ));
    String result = post(conf.getEmbeddingUrl(), payload);
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

  private String post(String url, String payload) throws IOException, InterruptedException {
    String fullUrl = url.contains("?") ? url + "&access_token=" + token() : url + "?access_token=" + token();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(fullUrl))
        .timeout(Duration.ofSeconds(60))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException("Baidu API error: " + response.statusCode() + " " + response.body());
    }
    return response.body();
  }

  private String encode(String raw) {
    return URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8);
  }
}
