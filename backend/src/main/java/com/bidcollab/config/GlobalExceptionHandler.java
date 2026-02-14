package com.bidcollab.config;

import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(EntityNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> handleNotFound(EntityNotFoundException ex) {
    return Map.of("message", "Not found");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleBadRequest(IllegalArgumentException ex) {
    return Map.of("message", ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> handleConflict(IllegalStateException ex) {
    return Map.of("message", ex.getMessage());
  }

  @ExceptionHandler({ MaxUploadSizeExceededException.class, MultipartException.class })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> handleUploadTooLarge(Exception ex) {
    return Map.of("message", "上传文件过大，当前单文件上限为100MB，请压缩后重试。");
  }
}
