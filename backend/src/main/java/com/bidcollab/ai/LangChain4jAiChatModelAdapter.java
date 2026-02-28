package com.bidcollab.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LangChain4jAiChatModelAdapter implements ChatModel {

  private final AiClient aiClient;

  public LangChain4jAiChatModelAdapter(AiClient aiClient) {
    this.aiClient = aiClient;
  }

  @Override
  public ChatResponse chat(ChatRequest chatRequest) {
    List<ChatMessage> messages = chatRequest == null ? List.of() : chatRequest.messages();
    String systemPrompt = SystemMessage.findLast(messages).map(SystemMessage::text).orElse("");
    String userPrompt = messages.stream()
        .filter(m -> m instanceof UserMessage)
        .map(m -> ((UserMessage) m).hasSingleText() ? ((UserMessage) m).singleText() : "")
        .filter(s -> s != null && !s.isBlank())
        .collect(Collectors.joining("\n\n"));
    if (userPrompt.isBlank()) {
      userPrompt = messages.stream().map(String::valueOf).collect(Collectors.joining("\n"));
    }
    String content = aiClient.chat(systemPrompt, userPrompt);
    int input = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
    int output = estimateTokens(content);
    return ChatResponse.builder()
        .aiMessage(AiMessage.from(content))
        .tokenUsage(new TokenUsage(input, output, input + output))
        .build();
  }

  private int estimateTokens(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    return Math.max(1, (int) Math.ceil(text.length() / 1.8d));
  }
}

