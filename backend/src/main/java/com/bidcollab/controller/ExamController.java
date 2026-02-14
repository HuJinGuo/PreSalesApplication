package com.bidcollab.controller;

import com.bidcollab.dto.ExamGenerateRequest;
import com.bidcollab.dto.ExamPaperResponse;
import com.bidcollab.dto.ExamPublishResponse;
import com.bidcollab.dto.ExamSubmissionResponse;
import com.bidcollab.dto.ExamSubmitRequest;
import com.bidcollab.service.ExamService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exams")
public class ExamController {
  private final ExamService examService;

  public ExamController(ExamService examService) {
    this.examService = examService;
  }

  @PostMapping("/generate")
  public ExamPaperResponse generate(@Valid @RequestBody ExamGenerateRequest request) {
    return examService.generate(request);
  }

  @GetMapping
  public List<ExamPaperResponse> list(@RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
    return examService.listPapers(knowledgeBaseId);
  }

  @GetMapping("/{paperId}")
  public ExamPaperResponse getPaper(@PathVariable("paperId") Long paperId) {
    return examService.getPaper(paperId);
  }

  @PostMapping("/{paperId}/publish")
  public ExamPublishResponse publish(@PathVariable("paperId") Long paperId) {
    return examService.publish(paperId);
  }

  @DeleteMapping("/{paperId}")
  public void delete(@PathVariable("paperId") Long paperId) {
    examService.deletePaper(paperId);
  }

  @PostMapping("/{paperId}/submit")
  public ExamSubmissionResponse submit(@PathVariable("paperId") Long paperId, @Valid @RequestBody ExamSubmitRequest request) {
    return examService.submit(paperId, request);
  }

  @GetMapping("/{paperId}/submissions")
  public List<ExamSubmissionResponse> listSubmissions(@PathVariable("paperId") Long paperId) {
    return examService.listSubmissions(paperId);
  }

  @GetMapping("/submissions/{submissionId}")
  public ExamSubmissionResponse getSubmission(@PathVariable("submissionId") Long submissionId) {
    return examService.getSubmission(submissionId);
  }
}
