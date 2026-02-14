package com.bidcollab.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SectionTemplateNode {
  private String title;
  private List<SectionTemplateNode> children = new ArrayList<>();
}

