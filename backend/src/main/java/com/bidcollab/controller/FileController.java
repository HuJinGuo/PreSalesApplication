package com.bidcollab.controller;

import com.bidcollab.dto.FileUploadResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
  private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;

  private final String uploadDir;

  public FileController(@Value("${app.upload.storage-dir:/tmp/bid-doc-uploads}") String uploadDir) {
    this.uploadDir = uploadDir;
  }

  @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public FileUploadResponse uploadImage(@RequestParam("file") MultipartFile file) {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("上传文件不能为空");
    }
    if (file.getSize() > MAX_IMAGE_SIZE) {
      throw new IllegalArgumentException("图片大小不能超过10MB");
    }
    String originalName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
    String extension = extensionOf(originalName);
    if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
      throw new IllegalArgumentException("仅支持 jpg/jpeg/png/gif/webp/bmp 图片");
    }

    LocalDate now = LocalDate.now();
    String dateDir = now.format(DateTimeFormatter.BASIC_ISO_DATE);
    Path targetDir = Path.of(uploadDir, "images", dateDir);
    try {
      Files.createDirectories(targetDir);
      String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
      Path target = targetDir.resolve(fileName);
      Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
      String url = "/files/images/" + dateDir + "/" + fileName;
      return FileUploadResponse.builder()
          .url(url)
          .originalName(originalName)
          .size(file.getSize())
          .build();
    } catch (IOException ex) {
      throw new IllegalStateException("图片上传失败", ex);
    }
  }

  private String extensionOf(String filename) {
    int idx = filename.lastIndexOf('.');
    if (idx < 0 || idx == filename.length() - 1) {
      return "png";
    }
    return filename.substring(idx + 1);
  }
}
