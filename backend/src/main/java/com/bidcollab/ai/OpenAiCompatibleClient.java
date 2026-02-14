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

  OpenAiCompatibleClient(String baseUrl, String apiKey) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
  }

  String chat(String model, String systemPrompt, String userPrompt) throws IOException, InterruptedException {
    Map<String, Object> body = Map.of(
        "model", model,
        "messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        )
    );
    String result = postJson(baseUrl + "/chat/completions", body);
    JsonNode root = MAPPER.readTree(result);
    return root.path("choices").path(0).path("message").path("content").asText();
  }

  List<Double> embedding(String model, String text) throws IOException, InterruptedException {
    Map<String, Object> body = Map.of(
        "model", model,
        "input", text
    );
    String result = postJson(baseUrl + "/embeddings", body);
    JsonNode arr = MAPPER.readTree(result).path("data").path(0).path("embedding");
    List<Double> vec = new ArrayList<>();
    for (JsonNode n : arr) {
      vec.add(n.asDouble());
    }
    return vec;
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
