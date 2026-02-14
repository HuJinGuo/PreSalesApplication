package com.bidcollab.controller;

import com.bidcollab.dto.AssetCreateRequest;
import com.bidcollab.dto.AssetResponse;
import com.bidcollab.dto.ReuseRequest;
import com.bidcollab.entity.SectionReuseTrace;
import com.bidcollab.service.AssetService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AssetController {
  private final AssetService assetService;

  public AssetController(AssetService assetService) {
    this.assetService = assetService;
  }

  @PostMapping("/sections/{sectionId}/asset")
  public AssetResponse createAsset(@PathVariable("sectionId") Long sectionId, @Valid @RequestBody AssetCreateRequest request) {
    return assetService.createAsset(sectionId, request);
  }

  @GetMapping("/assets/search")
  public List<AssetResponse> search(@RequestParam(name = "industry", required = false) String industry,
                                    @RequestParam(name = "scope", required = false) String scope,
                                    @RequestParam(name = "keyword", required = false) String keyword) {
    return assetService.search(industry, scope, keyword);
  }

  @PostMapping("/sections/{sectionId}/reuse")
  public Long reuse(@PathVariable("sectionId") Long sectionId, @Valid @RequestBody ReuseRequest request) {
    request.setTargetParentId(sectionId);
    return assetService.reuse(request);
  }

  @GetMapping("/sections/{sectionId}/reuse-trace")
  public List<SectionReuseTrace> reuseTrace(@PathVariable("sectionId") Long sectionId) {
    return assetService.listReuseTrace(sectionId);
  }
}
