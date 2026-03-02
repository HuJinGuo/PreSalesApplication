package com.bidcollab.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 线程池统一配置类。
 * <p>
 * 将业务线程池的创建和生命周期管理集中于此，避免在各 Service 中硬编码线程池逻辑。
 * Spring 容器会在 Bean 销毁时自动调用 {@link ExecutorService#shutdown()}（通过
 * destroyMethod）。
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * Embedding 向量化专用线程池。
     * <p>
     * 用于并行调用 AI Embedding API，提升批量文档索引速度。
     * 并发数可通过 {@code app.knowledge.embedding-concurrency} 配置，默认 6。
     *
     * @param concurrency 并发线程数
     * @return 固定大小的线程池
     */
    @Bean(name = "embeddingExecutor", destroyMethod = "shutdown")
    public ExecutorService embeddingExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("embedding-worker-", 0).factory());
    }
    // public ExecutorService embeddingExecutor(
    // @Value("${app.knowledge.embedding-concurrency:6}") int concurrency) {
    // int poolSize = Math.max(1, concurrency);
    // return Executors.newFixedThreadPool(poolSize, r -> {
    // Thread t = new Thread(r, "embedding-worker");
    // t.setDaemon(true);
    // return t;
    // });
    // }

    /**
     * 知识库重建索引任务线程池（文档级）。
     * <p>
     * 用于异步执行“解析+分块+向量化+关键词缓存”，避免上传接口长时间阻塞。
     */
    @Bean(name = "knowledgeReindexExecutor", destroyMethod = "shutdown")
    public ExecutorService knowledgeReindexExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("knowledge-reindex-worker-", 0).factory());
    }
    // public ExecutorService knowledgeReindexExecutor(
    // @Value("${app.knowledge.reindex-concurrency:2}") int concurrency) {
    // int poolSize = Math.max(1, concurrency);
    // return Executors.newFixedThreadPool(poolSize, r -> {
    // Thread t = new Thread(r, "knowledge-reindex-worker");
    // t.setDaemon(true);
    // return t;
    // });
    // }

    @Bean(name = "aiDocumentExecutor", destroyMethod = "shutdown")
    public ExecutorService aiDocumentExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("ai-document-worker-", 0).factory());
    }
    // public ExecutorService aiDocumentExecutor(
    // @Value("${app.ai.document-auto-write-concurrency:2}") int concurrency) {
    // int poolSize = Math.max(1, concurrency);
    // return Executors.newFixedThreadPool(poolSize, r -> {
    // Thread t = new Thread(r, "ai-document-worker");
    // t.setDaemon(true);
    // return t;
    // });
    // }

    @Bean(name = "agentToolExecutor", destroyMethod = "shutdown")
    public ExecutorService agentToolExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("agent-tool-worker-", 0).factory());
    }
    // public ExecutorService agentToolExecutor(
    // @Value("${app.agent.runtime.tool-concurrency:6}") int concurrency) {
    // int poolSize = Math.max(1, concurrency);
    // return Executors.newFixedThreadPool(poolSize, r -> {
    // Thread t = new Thread(r, "agent-tool-worker");
    // t.setDaemon(true);
    // return t;
    // });
    // }
}
