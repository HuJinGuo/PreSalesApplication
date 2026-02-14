package com.bidcollab.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;

public final class VectorUtil {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private VectorUtil() {
  }

  public static String toJson(List<Double> vector) {
    try {
      return MAPPER.writeValueAsString(vector);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Serialize vector failed", e);
    }
  }

  public static List<Double> fromJson(String json) {
    try {
      return MAPPER.readValue(json, new TypeReference<List<Double>>() {});
    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  public static double cosine(List<Double> a, List<Double> b) {
    if (a.isEmpty() || b.isEmpty()) {
      return 0;
    }
    int n = Math.min(a.size(), b.size());
    double dot = 0;
    double na = 0;
    double nb = 0;
    for (int i = 0; i < n; i++) {
      double va = a.get(i);
      double vb = b.get(i);
      dot += va * vb;
      na += va * va;
      nb += vb * vb;
    }
    if (na == 0 || nb == 0) {
      return 0;
    }
    return dot / (Math.sqrt(na) * Math.sqrt(nb));
  }
}
