package com.bidcollab.ai;

import java.util.function.Supplier;

/**
 * AI 调用链路上下文（线程本地）。
 * 用于将知识库文档/章节/任务等业务标识透传到统一调用埋点。
 */
public final class AiTraceContext {
  private static final ThreadLocal<AiTraceMeta> HOLDER = new ThreadLocal<>();

  private AiTraceContext() {
  }

  public static AiTraceMeta current() {
    return HOLDER.get();
  }

  public static <T> T with(AiTraceMeta meta, Supplier<T> supplier) {
    AiTraceMeta previous = HOLDER.get();
    HOLDER.set(meta);
    try {
      return supplier.get();
    } finally {
      restore(previous);
    }
  }

  public static void with(AiTraceMeta meta, Runnable runnable) {
    AiTraceMeta previous = HOLDER.get();
    HOLDER.set(meta);
    try {
      runnable.run();
    } finally {
      restore(previous);
    }
  }

  private static void restore(AiTraceMeta previous) {
    if (previous == null) {
      HOLDER.remove();
    } else {
      HOLDER.set(previous);
    }
  }

  public record AiTraceMeta(
      Long knowledgeBaseId,
      Long knowledgeDocumentId,
      Long sectionId,
      Long aiTaskId,
      Integer retryCount,
      String bizTag) {
  }
}
