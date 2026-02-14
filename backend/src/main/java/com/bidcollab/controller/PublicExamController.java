package com.bidcollab.controller;

import com.bidcollab.dto.ExamPaperResponse;
import com.bidcollab.dto.ExamSubmissionResponse;
import com.bidcollab.dto.PublicExamSubmitRequest;
import com.bidcollab.service.ExamService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/exams")
public class PublicExamController {
  private final ExamService examService;

  public PublicExamController(ExamService examService) {
    this.examService = examService;
  }

  @GetMapping("/{shareToken}")
  public ExamPaperResponse getPaper(@PathVariable("shareToken") String shareToken) {
    return examService.getPublicPaper(shareToken);
  }

  @PostMapping("/{shareToken}/submit")
  public ExamSubmissionResponse submit(@PathVariable("shareToken") String shareToken,
                                       @Valid @RequestBody PublicExamSubmitRequest request) {
    return examService.submitPublic(shareToken, request);
  }

  @GetMapping("/{shareToken}/submissions/{submissionId}")
  public ExamSubmissionResponse getSubmission(@PathVariable("shareToken") String shareToken,
                                              @PathVariable("submissionId") Long submissionId) {
    return examService.getPublicSubmission(shareToken, submissionId);
  }

  @GetMapping("/{shareToken}/leaderboard")
  public List<ExamSubmissionResponse> leaderboard(@PathVariable("shareToken") String shareToken) {
    return examService.listPublicSubmissions(shareToken);
  }
}
