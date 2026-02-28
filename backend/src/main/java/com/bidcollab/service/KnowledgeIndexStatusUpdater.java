package com.bidcollab.service;

@FunctionalInterface
public interface KnowledgeIndexStatusUpdater {
  boolean update(Long documentId, String taskId, String status, String message, Integer progress);
}

