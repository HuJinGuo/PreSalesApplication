package com.bidcollab.export;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class ExportImageLoader {
  private final String uploadDir;
  private final HttpClient httpClient;

  public ExportImageLoader(String uploadDir) {
    this.uploadDir = uploadDir;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public byte[] load(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      String trimmed = url.trim();
      if (trimmed.startsWith("/files/")) {
        return loadLocal(trimmed.substring("/files/".length()));
      }
      if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        URI uri = URI.create(trimmed);
        if (uri.getPath() != null && uri.getPath().startsWith("/files/")) {
          return loadLocal(uri.getPath().substring("/files/".length()));
        }
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).GET().build();
        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        return resp.statusCode() >= 200 && resp.statusCode() < 300 ? resp.body() : null;
      }
      if (trimmed.startsWith("file:")) {
        return Files.readAllBytes(Path.of(URI.create(trimmed)));
      }
      return loadLocal(trimmed);
    } catch (Exception ex) {
      return null;
    }
  }

  private byte[] loadLocal(String relativePath) throws IOException {
    String normalized = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
    Path file = Path.of(uploadDir).resolve(normalized).normalize();
    return Files.exists(file) ? Files.readAllBytes(file) : null;
  }
}
