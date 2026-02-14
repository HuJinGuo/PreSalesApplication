package com.bidcollab.export;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FlattenedSection {
  private String number;
  private String title;
  private String content;
  private int level;
}
