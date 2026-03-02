package com.bidcollab.agent.gateway;

import com.bidcollab.ai.AiClient;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModelGateway {
  private final AiClient aiClient;

  public ModelGateway(AiClient aiClient) {
    this.aiClient = aiClient;
  }

  public String chat(String systemPrompt, String userPrompt) {
    return aiClient.chat(systemPrompt, userPrompt);
  }

  public List<Double> embedding(String text) {
    return aiClient.embedding(text);
  }
}
