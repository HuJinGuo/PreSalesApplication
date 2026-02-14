package com.bidcollab.ai;

import java.util.List;

public interface AiClient {
  String chat(String systemPrompt, String userPrompt);
  List<Double> embedding(String text);
}
