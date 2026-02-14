package com.bidcollab.dto;

import com.bidcollab.enums.SectionStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SectionTreeNode {
  private Long id;
  private Long documentId;
  private Long parentId;
  private String title;
  private Integer level;
  private Integer sortIndex;
  private Long currentVersionId;
  private SectionStatus status;
  private List<SectionTreeNode> children = new ArrayList<>();
}
