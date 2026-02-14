package com.bidcollab.service;

import com.bidcollab.ai.AiClient;
import com.bidcollab.dto.ExamGenerateRequest;
import com.bidcollab.dto.ExamPaperResponse;
import com.bidcollab.dto.ExamPublishResponse;
import com.bidcollab.dto.ExamQuestionResponse;
import com.bidcollab.dto.ExamSubmissionResponse;
import com.bidcollab.dto.ExamSubmitRequest;
import com.bidcollab.dto.PublicExamSubmitRequest;
import com.bidcollab.entity.ExamPaper;
import com.bidcollab.entity.ExamQuestion;
import com.bidcollab.entity.ExamSubmission;
import com.bidcollab.entity.KnowledgeBase;
import com.bidcollab.entity.KnowledgeChunk;
import com.bidcollab.entity.User;
import com.bidcollab.enums.ExamSubmissionStatus;
import com.bidcollab.enums.RepeatSubmissionStrategy;
import com.bidcollab.repository.ExamPaperRepository;
import com.bidcollab.repository.ExamQuestionRepository;
import com.bidcollab.repository.ExamSubmissionRepository;
import com.bidcollab.repository.KnowledgeBaseRepository;
import com.bidcollab.repository.KnowledgeChunkRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamService {
  // [AI-READ] 命题与判卷核心服务；后续可按此标记统一检索/删除学习注释。
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsHan}A-Za-z0-9]+");
  private static final Set<String> STOPWORDS = Set.of(
      "这是", "这个", "那个", "一种", "其中", "以及", "可以", "用于", "通过", "为了",
      "spring", "java", "xml", "bean", "property", "config", "annotation", "vs", "getworker", "javaconfig");

  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final KnowledgeChunkRepository knowledgeChunkRepository;
  private final ExamPaperRepository examPaperRepository;
  private final ExamQuestionRepository examQuestionRepository;
  private final ExamSubmissionRepository examSubmissionRepository;
  private final CurrentUserService currentUserService;
  private final AiClient aiClient;
  private final RepeatSubmissionStrategy repeatSubmissionStrategy;

  public ExamService(KnowledgeBaseRepository knowledgeBaseRepository,
      KnowledgeChunkRepository knowledgeChunkRepository,
      ExamPaperRepository examPaperRepository,
      ExamQuestionRepository examQuestionRepository,
      ExamSubmissionRepository examSubmissionRepository,
      CurrentUserService currentUserService,
      AiClient aiClient,
      @Value("${app.exam.repeat-strategy:ALL}") String repeatSubmissionStrategy) {
    this.knowledgeBaseRepository = knowledgeBaseRepository;
    this.knowledgeChunkRepository = knowledgeChunkRepository;
    this.examPaperRepository = examPaperRepository;
    this.examQuestionRepository = examQuestionRepository;
    this.examSubmissionRepository = examSubmissionRepository;
    this.currentUserService = currentUserService;
    this.aiClient = aiClient;
    this.repeatSubmissionStrategy = parseRepeatStrategy(repeatSubmissionStrategy);
  }

  @Transactional
  public ExamPaperResponse generate(ExamGenerateRequest request) {
    // [AI-READ] 生成试卷：知识块汇总 -> AI生成 -> 质量清洗 -> 回退补题 -> 持久化。
    KnowledgeBase kb = knowledgeBaseRepository.findById(request.getKnowledgeBaseId())
        .orElseThrow(EntityNotFoundException::new);
    List<KnowledgeChunk> chunks = knowledgeChunkRepository.findByKnowledgeBaseId(kb.getId()).stream()
        .sorted(Comparator.comparing(KnowledgeChunk::getCreatedAt).reversed())
        .limit(50)
        .collect(Collectors.toList());

    String knowledgeContext = chunks.stream().map(KnowledgeChunk::getContent).collect(Collectors.joining("\n\n"));
    List<GeneratedQuestion> aiGenerated = generateQuestionsByAi(request, knowledgeContext);
    List<GeneratedQuestion> generated = enforceQuestionQuality(request, aiGenerated, chunks);

    BigDecimal totalScore = generated.stream().map(q -> q.score).reduce(BigDecimal.ZERO, BigDecimal::add);

    ExamPaper paper = ExamPaper.builder()
        .knowledgeBase(kb)
        .title(request.getTitle())
        .instructions(request.getInstructions())
        .totalScore(totalScore)
        .generatedBy(currentUserService.getCurrentUserId())
        .published(false)
        .build();
    examPaperRepository.save(paper);

    for (int i = 0; i < generated.size(); i++) {
      GeneratedQuestion g = generated.get(i);
      examQuestionRepository.save(ExamQuestion.builder()
          .paper(paper)
          .questionType(g.type)
          .stem(g.stem)
          .optionsJson(g.optionsJson)
          .referenceAnswer(g.answer)
          .score(g.score)
          .sortIndex(i + 1)
          .build());
    }
    return getPaper(paper.getId());
  }

  @Transactional
  public ExamPublishResponse publish(Long paperId) {
    ExamPaper paper = examPaperRepository.findById(paperId).orElseThrow(EntityNotFoundException::new);
    if (paper.getShareToken() == null || paper.getShareToken().isBlank()) {
      paper.setShareToken(UUID.randomUUID().toString().replace("-", ""));
    }
    paper.setPublished(true);
    paper.setPublishedAt(Instant.now());
    return ExamPublishResponse.builder()
        .paperId(paper.getId())
        .shareToken(paper.getShareToken())
        .sharePath("/exam-link/" + paper.getShareToken())
        .build();
  }

  @Transactional
  public void deletePaper(Long paperId) {
    examPaperRepository.findById(paperId).orElseThrow(EntityNotFoundException::new);
    examSubmissionRepository.deleteByPaperId(paperId);
    examQuestionRepository.deleteByPaperId(paperId);
    examPaperRepository.deleteById(paperId);
  }

  public List<ExamPaperResponse> listPapers(Long kbId) {
    return examPaperRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(kbId).stream()
        .map(p -> toPaperResponse(p, examQuestionRepository.findByPaperIdOrderBySortIndexAsc(p.getId())))
        .collect(Collectors.toList());
  }

  public ExamPaperResponse getPaper(Long paperId) {
    ExamPaper paper = examPaperRepository.findById(paperId).orElseThrow(EntityNotFoundException::new);
    return toPaperResponse(paper, examQuestionRepository.findByPaperIdOrderBySortIndexAsc(paperId));
  }

  public ExamPaperResponse getPublicPaper(String shareToken) {
    ExamPaper paper = findPublishedPaperByToken(shareToken);
    return toPaperResponse(paper, examQuestionRepository.findByPaperIdOrderBySortIndexAsc(paper.getId()));
  }

  @Transactional
  public ExamSubmissionResponse submit(Long paperId, ExamSubmitRequest request) {
    ExamPaper paper = examPaperRepository.findById(paperId).orElseThrow(EntityNotFoundException::new);
    Long userId = currentUserService.getCurrentUserId();
    String submitterName = currentUserService.getCurrentUser().map(User::getUsername)
        .orElseGet(() -> userId == null ? null : ("user-" + userId));
    return gradeAndSave(paper, request.getAnswersJson(), userId, submitterName);
  }

  @Transactional
  public ExamSubmissionResponse submitPublic(String shareToken, PublicExamSubmitRequest request) {
    ExamPaper paper = findPublishedPaperByToken(shareToken);
    return gradeAndSave(paper, request.getAnswersJson(), null, request.getSubmitterName());
  }

  public ExamSubmissionResponse getSubmission(Long submissionId) {
    return toSubmissionResponse(
        examSubmissionRepository.findById(submissionId).orElseThrow(EntityNotFoundException::new));
  }

  public ExamSubmissionResponse getPublicSubmission(String shareToken, Long submissionId) {
    ExamSubmission submission = examSubmissionRepository.findById(submissionId)
        .orElseThrow(EntityNotFoundException::new);
    ExamPaper paper = findPublishedPaperByToken(shareToken);
    if (!submission.getPaper().getId().equals(paper.getId())) {
      throw new IllegalArgumentException("Submission does not belong to this paper");
    }
    return toSubmissionResponse(submission);
  }

  public List<ExamSubmissionResponse> listSubmissions(Long paperId) {
    examPaperRepository.findById(paperId).orElseThrow(EntityNotFoundException::new);
    return rankSubmissions(examSubmissionRepository.findByPaperIdOrderBySubmittedAtDesc(paperId));
  }

  public List<ExamSubmissionResponse> listPublicSubmissions(String shareToken) {
    ExamPaper paper = findPublishedPaperByToken(shareToken);
    return rankSubmissions(examSubmissionRepository.findByPaperIdOrderBySubmittedAtDesc(paper.getId()));
  }

  private ExamSubmissionResponse gradeAndSave(ExamPaper paper, String answersJson, Long submitterId,
      String submitterName) {
    List<ExamQuestion> questions = examQuestionRepository.findByPaperIdOrderBySortIndexAsc(paper.getId());
    GradeResult grade = gradeSubmission(questions, answersJson);
    Instant now = Instant.now();
    String ownerKey = ownerKey(submitterId, submitterName);

    List<ExamSubmission> ownerSubmissions = examSubmissionRepository.findByPaperIdOrderBySubmittedAtDesc(paper.getId())
        .stream()
        .filter(s -> ownerKey.equals(ownerKey(s.getSubmitterId(), s.getSubmitterName())))
        .collect(Collectors.toList());

    ExamSubmission submission;
    if (repeatSubmissionStrategy == RepeatSubmissionStrategy.ALL || ownerSubmissions.isEmpty()) {
      submission = ExamSubmission.builder()
          .paper(paper)
          .submitterId(submitterId)
          .submitterName(submitterName)
          .answersJson(answersJson)
          .status(ExamSubmissionStatus.GRADED)
          .submittedAt(now)
          .gradedAt(now)
          .score(grade.score)
          .aiFeedback(grade.feedback)
          .build();
      examSubmissionRepository.save(submission);
      return toSubmissionResponse(submission);
    }

    if (repeatSubmissionStrategy == RepeatSubmissionStrategy.LATEST) {
      submission = ownerSubmissions.stream().max(Comparator.comparing(ExamSubmission::getSubmittedAt))
          .orElse(ownerSubmissions.get(0));
      updateSubmission(submission, submitterId, submitterName, answersJson, grade, now);
      deleteOtherOwnerSubmissions(ownerSubmissions, submission);
      return toSubmissionResponse(submission);
    }

    submission = ownerSubmissions.stream()
        .max(Comparator.comparing(s -> s.getScore() == null ? BigDecimal.valueOf(-1) : s.getScore()))
        .orElse(ownerSubmissions.get(0));
    BigDecimal best = submission.getScore() == null ? BigDecimal.valueOf(-1) : submission.getScore();
    if (grade.score.compareTo(best) > 0) {
      updateSubmission(submission, submitterId, submitterName, answersJson, grade, now);
    }
    deleteOtherOwnerSubmissions(ownerSubmissions, submission);
    return toSubmissionResponse(submission);
  }

  private GradeResult gradeSubmission(List<ExamQuestion> questions, String answersJson) {
    try {
      Map<String, String> answers = MAPPER.readValue(answersJson, new TypeReference<Map<String, String>>() {
      });
      BigDecimal total = BigDecimal.ZERO;
      StringBuilder fb = new StringBuilder("自动判卷结果:\n");
      for (ExamQuestion q : questions) {
        String key = String.valueOf(q.getId());
        String ans = answers.getOrDefault(key, "").trim();
        BigDecimal get = gradeByType(q, ans);
        total = total.add(get);
        fb.append("Q").append(q.getSortIndex()).append(": ").append(get).append("/").append(q.getScore()).append("\n");
      }
      return new GradeResult(total, fb.toString());
    } catch (Exception ex) {
      return new GradeResult(BigDecimal.ZERO, "自动判卷失败");
    }
  }

  private BigDecimal gradeByType(ExamQuestion q, String ans) {
    String type = q.getQuestionType() == null ? "" : q.getQuestionType().toUpperCase();
    String ref = q.getReferenceAnswer() == null ? "" : q.getReferenceAnswer().trim();
    if ("SINGLE".equals(type) || "JUDGE".equals(type)) {
      return ans.equalsIgnoreCase(ref) ? q.getScore() : BigDecimal.ZERO;
    }
    if ("BLANK".equals(type)) {
      if (ans.isBlank() || ref.isBlank()) {
        return BigDecimal.ZERO;
      }
      boolean hit = ans.equalsIgnoreCase(ref) || ans.contains(ref) || ref.contains(ans);
      return hit ? q.getScore() : BigDecimal.ZERO;
    }
    if ("ESSAY".equals(type)) {
      return evaluateEssay(q, ans);
    }
    return BigDecimal.ZERO;
  }

  private BigDecimal evaluateEssay(ExamQuestion q, String ans) {
    // [AI-READ] 主路径使用 AI 评分；失败时走规则兜底，避免固定分。
    if (ans == null || ans.isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      String prompt = "你是企业培训阅卷老师。请按评分要点严格打分，不得给满分除非答案完整。\n"
          + "题目:" + q.getStem() + "\n参考要点:" + (q.getReferenceAnswer() == null ? "" : q.getReferenceAnswer())
          + "\n学生答案:" + ans + "\n满分:" + q.getScore()
          + "\n仅输出JSON:{\"score\":数字,\"reason\":\"不超过40字\"}";
      String raw = aiClient.chat("你是阅卷老师，按参考要点评分。", prompt);
      JsonNode node = MAPPER.readTree(extractJsonObject(raw));
      double s = node.path("score").asDouble(0);
      double max = q.getScore() == null ? 0 : q.getScore().doubleValue();
      s = Math.max(0, Math.min(max, s));
      return BigDecimal.valueOf(s).setScale(2, RoundingMode.HALF_UP);
    } catch (Exception ex) {
      List<String> keys = tokenizeKeywords(q.getReferenceAnswer());
      int hit = 0;
      for (String k : keys) {
        if (ans.toLowerCase().contains(k.toLowerCase())) {
          hit++;
        }
      }
      if (ans.length() < 20) {
        return BigDecimal.ZERO;
      }
      long sections = ans.chars().filter(c -> c == '。' || c == '；' || c == '\n').count() + 1;
      double structureRatio = Math.min(1.0, sections / 4.0);
      double lengthRatio = Math.min(1.0, ans.length() / 220.0);
      double keyRatio = keys.isEmpty() ? 0.0 : Math.min(1.0, (double) hit / keys.size());
      double ratio;
      if (keys.isEmpty()) {
        ratio = 0.1 + 0.5 * lengthRatio + 0.4 * structureRatio;
      } else {
        ratio = 0.1 + 0.55 * keyRatio + 0.2 * lengthRatio + 0.15 * structureRatio;
      }
      ratio = Math.max(0, Math.min(1.0, ratio));
      return q.getScore().multiply(BigDecimal.valueOf(ratio)).setScale(2, RoundingMode.HALF_UP);
    }
  }

  private List<GeneratedQuestion> generateQuestionsByAi(ExamGenerateRequest request, String context) {
    try {
      String cleanContext = sanitizeKnowledgeText(context);
      String systemPrompt = "你是企业培训命题专家。根据知识要点出题，但禁止照抄原文。题干必须自然、完整。只输出JSON。";
      String userPrompt = "知识要点:\n" + cleanContext + "\n\n"
          + "要求:\n"
          + "1) SINGLE 必须4个中文短句选项，且只有1个正确答案。\n"
          + "2) JUDGE 题答案只允许“正确/错误”。\n"
          + "3) BLANK 题必须有明确唯一关键词答案。\n"
          + "4) ESSAY 题参考答案需包含评分要点，不少于3条。\n"
          + "5) 禁止出现“根据知识库内容/依据原文”等措辞。\n"
          + "格式:{\"questions\":[{\"type\":\"SINGLE|JUDGE|BLANK|ESSAY\",\"stem\":\"...\",\"options\":[...],\"answer\":\"...\",\"score\":5}]}\n"
          + "数量: SINGLE=" + request.getSingleChoiceCount() + ", JUDGE=" + request.getJudgeCount()
          + ", BLANK=" + request.getBlankCount() + ", ESSAY=" + request.getEssayCount();
      String raw = aiClient.chat(systemPrompt, userPrompt);
      JsonNode root = MAPPER.readTree(extractJsonObject(raw));
      List<GeneratedQuestion> result = new ArrayList<>();
      for (JsonNode q : root.path("questions")) {
        String type = q.path("type").asText();
        String stem = sanitizeQuestionStem(q.path("stem").asText());
        String answer = q.path("answer").asText();
        String optionsJson = q.has("options") ? MAPPER.writeValueAsString(q.get("options")) : null;
        BigDecimal score = BigDecimal.valueOf(q.path("score").asDouble(5.0)).setScale(2, RoundingMode.HALF_UP);
        result.add(new GeneratedQuestion(type, stem, optionsJson, answer, score));
      }
      return result;
    } catch (Exception ex) {
      return List.of();
    }
  }

  private List<GeneratedQuestion> enforceQuestionQuality(ExamGenerateRequest req, List<GeneratedQuestion> aiGenerated,
      List<KnowledgeChunk> chunks) {
    List<FactItem> facts = extractFactItems(chunks);
    if (facts.isEmpty()) {
      facts = List.of(
          new FactItem("实施规范", "在实施前应明确目标、边界与验收标准，并形成可追踪记录。"),
          new FactItem("风险控制", "关键风险应在方案阶段识别，并配置监控与应急机制。"),
          new FactItem("质量管理", "过程数据需要完整留痕，保证结果可复盘、可审计。"));
    }

    List<GeneratedQuestion> cleaned = new ArrayList<>();
    Set<String> dedup = new HashSet<>();
    for (GeneratedQuestion q : aiGenerated) {
      GeneratedQuestion n = normalizeQuestion(q);
      if (n == null)
        continue;
      String k = n.type + "|" + n.stem.replaceAll("\\s+", "").toLowerCase();
      if (dedup.add(k))
        cleaned.add(n);
    }

    List<GeneratedQuestion> out = new ArrayList<>();
    int seed = 0;
    seed = appendByType(out, cleaned, "SINGLE", safeCount(req.getSingleChoiceCount()), facts, seed);
    seed = appendByType(out, cleaned, "JUDGE", safeCount(req.getJudgeCount()), facts, seed);
    seed = appendByType(out, cleaned, "BLANK", safeCount(req.getBlankCount()), facts, seed);
    appendByType(out, cleaned, "ESSAY", safeCount(req.getEssayCount()), facts, seed);
    return out;
  }

  private int appendByType(List<GeneratedQuestion> out, List<GeneratedQuestion> cleaned, String type, int need,
      List<FactItem> facts, int seed) {
    int added = 0;
    for (GeneratedQuestion q : cleaned) {
      if (type.equals(q.type)) {
        out.add(q);
        added++;
      }
      if (added >= need)
        break;
    }
    if (added < need) {
      List<GeneratedQuestion> fb = fallbackByType(type, need - added, facts, seed);
      out.addAll(fb);
      added += fb.size();
    }
    return seed + added;
  }

  private GeneratedQuestion normalizeQuestion(GeneratedQuestion q) {
    if (q == null || q.type == null || q.stem == null)
      return null;
    String type = q.type.trim().toUpperCase();
    String stem = sanitizeQuestionStem(q.stem);
    if (stem.length() < 8 || stem.length() > 180 || containsLowQualityToken(stem))
      return null;
    BigDecimal score = q.score == null || q.score.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.valueOf(5) : q.score;

    if ("SINGLE".equals(type)) {
      List<String> opts = parseOptions(q.optionsJson);
      if (opts.size() < 4 || opts.stream().anyMatch(o -> !isValidSingleOption(o)))
        return null;
      opts = new ArrayList<>(opts.subList(0, 4));
      String ans = normalizeSingleAnswer(q.answer, opts);
      if (ans == null)
        return null;
      return new GeneratedQuestion(type, stem, toJsonArray(opts), ans, score);
    }
    if ("JUDGE".equals(type)) {
      String ans = normalizeJudgeAnswer(q.answer);
      if (ans == null)
        return null;
      return new GeneratedQuestion(type, stem, "[\"正确\",\"错误\"]", ans, score);
    }
    if ("BLANK".equals(type)) {
      String ans = q.answer == null || q.answer.isBlank() ? "参考答案" : q.answer.trim();
      return new GeneratedQuestion(type, stem.contains("____") ? stem : stem + " ____", null, ans, score);
    }
    if ("ESSAY".equals(type)) {
      String ans = q.answer == null || q.answer.isBlank() ? "从目标、步骤、风险和验收四方面作答。" : q.answer.trim();
      return new GeneratedQuestion(type, stem, null, ans, score);
    }
    return null;
  }

  private List<GeneratedQuestion> fallbackByType(String type, int count, List<FactItem> facts, int seed) {
    List<GeneratedQuestion> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      FactItem fact = facts.get((seed + i) % facts.size());
      FactItem fact2 = facts.get((seed + i + 1) % facts.size());
      if ("SINGLE".equals(type)) {
        list.add(buildSingleFallback(fact, fact2, seed + i));
      } else if ("JUDGE".equals(type)) {
        boolean positive = ((seed + i) % 2 == 0);
        String stem = positive ? "判断对错：" + clauseFromStatement(fact.statement)
            : "判断对错：" + negateStatement(clauseFromStatement(fact2.statement));
        list.add(
            new GeneratedQuestion("JUDGE", stem, "[\"正确\",\"错误\"]", positive ? "正确" : "错误", BigDecimal.valueOf(2)));
      } else if ("BLANK".equals(type)) {
        String blank = chooseBlankTerm(clauseFromStatement(fact.statement));
        String stem = clauseFromStatement(fact.statement).replace(blank, "____");
        if (stem.equals(clauseFromStatement(fact.statement)))
          stem = "在" + fact.topic + "实践中，____ 是关键控制点。";
        list.add(new GeneratedQuestion("BLANK", stem, null, blank, BigDecimal.valueOf(5)));
      } else if ("ESSAY".equals(type)) {
        String stem = "请结合“" + fact.topic + "”主题，说明“" + shortSentence(clauseFromStatement(fact.statement))
            + "”与“" + shortSentence(clauseFromStatement(fact2.statement)) + "”在实施中的关系，并给出落地方案。";
        list.add(new GeneratedQuestion("ESSAY", stem, null,
            "建议至少覆盖：1)目标与边界定义 2)实施步骤 3)风险控制措施 4)验收指标。", BigDecimal.valueOf(10)));
      }
    }
    return list;
  }

  private GeneratedQuestion buildSingleFallback(FactItem fact, FactItem alt, int seed) {
    String stem = "在“" + fact.topic + "”实践中，哪项做法最符合规范？";
    String correct = clauseFromStatement(fact.statement);
    List<String> options = new ArrayList<>(List.of(
        buildAntiPatternOption(fact.topic, seed),
        shortSentence(negateStatement(correct)),
        clauseFromStatement(alt.statement),
        correct));
    options = options.stream().map(this::sanitizeKnowledgeText).distinct()
        .collect(Collectors.toCollection(ArrayList::new));
    while (options.size() < 4) {
      options.add("仅关注结果，不设过程验收标准。");
    }
    Collections.rotate(options, seed % options.size());
    int pos = options.indexOf(correct);
    if (pos < 0 || pos > 3) {
      pos = 0;
      options.set(0, correct);
    }
    if (options.size() > 4) {
      options = new ArrayList<>(options.subList(0, 4));
      int fixedPos = options.indexOf(correct);
      if (fixedPos < 0) {
        options.set(0, correct);
      }
    }
    int answerPos = Math.floorMod(seed, 4);
    Collections.swap(options, options.indexOf(correct), answerPos);
    return new GeneratedQuestion("SINGLE", stem, toJsonArray(options), String.valueOf((char) ('A' + answerPos)),
        BigDecimal.valueOf(5));
  }

  private List<FactItem> extractFactItems(List<KnowledgeChunk> chunks) {
    List<FactItem> facts = new ArrayList<>();
    Set<String> dedup = new HashSet<>();
    for (KnowledgeChunk c : chunks) {
      if (c.getContent() == null || c.getContent().isBlank())
        continue;
      String[] arr = c.getContent().replace("\r", "\n").split("[。！？；\\n]");
      for (String s : arr) {
        String clean = sanitizeKnowledgeText(s);
        if (clean.length() < 16 || clean.length() > 120 || containsLowQualityToken(clean))
          continue;
        if (!isLikelyNaturalSentence(clean))
          continue;
        String topic = pickKeyword(clean);
        if (topic.isBlank())
          continue;
        String key = (topic + "|" + clean).toLowerCase();
        if (dedup.add(key))
          facts.add(new FactItem(topic, clean));
        if (facts.size() >= 200)
          return facts;
      }
    }
    return facts;
  }

  private String pickKeyword(String text) {
    for (String t : tokenizeKeywords(text)) {
      if (t.length() >= 3 && t.length() <= 16)
        return t;
    }
    return "";
  }

  private List<String> tokenizeKeywords(String text) {
    if (text == null || text.isBlank())
      return List.of();
    String clean = sanitizeKnowledgeText(text).toLowerCase();
    List<String> tokens = new ArrayList<>();
    for (String raw : TOKEN_SPLIT.split(clean)) {
      String t = raw == null ? "" : raw.trim();
      if (t.length() < 2 || t.length() > 18)
        continue;
      if (t.length() <= 3 && t.matches("^[a-z]+$"))
        continue;
      if (STOPWORDS.contains(t))
        continue;
      if (t.matches("^\\d+$"))
        continue;
      if (t.matches("^[a-z]+$") && t.length() > 10)
        continue;
      tokens.add(t);
    }
    return tokens;
  }

  private boolean containsLowQualityToken(String text) {
    String lower = text.toLowerCase();
    if (lower.contains("getworker") || lower.contains("javaconfig") || lower.contains(" vs "))
      return true;
    if (lower.contains("http://") || lower.contains("https://"))
      return true;
    if (lower.contains("class ") || lower.contains("public ") || lower.contains("private "))
      return true;
    if (lower.contains("{") || lower.contains("}") || lower.contains("()") || lower.contains("=>"))
      return true;
    return tokenizeKeywords(text).isEmpty();
  }

  private String chooseBlankTerm(String statement) {
    List<String> keys = tokenizeKeywords(statement);
    for (String key : keys) {
      if (key.length() >= 2 && key.length() <= 10)
        return key;
    }
    return "关键环节";
  }

  private String shortSentence(String s) {
    String clean = sanitizeKnowledgeText(s);
    return clean.length() <= 56 ? clean : clean.substring(0, 56) + "...";
  }

  private String negateStatement(String s) {
    String clean = sanitizeKnowledgeText(s);
    if (clean.contains("必须"))
      return clean.replaceFirst("必须", "不必");
    if (clean.contains("需要"))
      return clean.replaceFirst("需要", "不需要");
    if (clean.contains("应"))
      return clean.replaceFirst("应", "不应");
    return "并非" + clean;
  }

  private List<String> parseOptions(String optionsJson) {
    if (optionsJson == null || optionsJson.isBlank())
      return List.of();
    try {
      List<String> values = MAPPER.readValue(optionsJson, new TypeReference<List<String>>() {
      });
      return values.stream()
          .map(v -> sanitizeKnowledgeText(v == null ? "" : v))
          .filter(v -> !v.isBlank())
          .filter(this::isValidSingleOption)
          .distinct()
          .collect(Collectors.toList());
    } catch (Exception ex) {
      return List.of();
    }
  }

  private String normalizeSingleAnswer(String answer, List<String> options) {
    if (answer == null)
      return null;
    String raw = answer.trim().toUpperCase();
    if (List.of("A", "B", "C", "D").contains(raw))
      return raw;
    for (int i = 0; i < options.size(); i++) {
      if (options.get(i).equalsIgnoreCase(answer.trim()))
        return String.valueOf((char) ('A' + i));
    }
    return null;
  }

  private String normalizeJudgeAnswer(String answer) {
    if (answer == null)
      return null;
    String raw = answer.trim();
    if ("正确".equals(raw) || "true".equalsIgnoreCase(raw) || "对".equals(raw))
      return "正确";
    if ("错误".equals(raw) || "false".equalsIgnoreCase(raw) || "错".equals(raw))
      return "错误";
    return null;
  }

  private String toJsonArray(List<String> values) {
    try {
      return MAPPER.writeValueAsString(values);
    } catch (Exception ex) {
      return "[]";
    }
  }

  private String sanitizeKnowledgeText(String input) {
    if (input == null)
      return "";
    return input.replaceAll("`{1,3}", "")
        .replaceAll("#+", "")
        .replaceAll("\\*{1,3}", "")
        .replaceAll("https?://\\S+", " ")
        .replaceAll("\\[|\\]|\\(|\\)", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private String sanitizeQuestionStem(String stem) {
    String s = sanitizeKnowledgeText(stem);
    return s.replace("根据知识库内容", "").replace("依据：", "").replace("原文", "").trim();
  }

  private boolean isLikelyNaturalSentence(String text) {
    double zhRatio = chineseRatio(text);
    if (zhRatio < 0.30)
      return false;
    if (text.matches(".*[{}<>;$].*"))
      return false;
    long alpha = text.chars().filter(Character::isLetter).count();
    long digits = text.chars().filter(Character::isDigit).count();
    return !(alpha > 0 && digits > 0 && ((double) digits / Math.max(1, alpha) > 0.4));
  }

  private boolean isValidSingleOption(String option) {
    if (option == null)
      return false;
    String s = sanitizeKnowledgeText(option);
    if (s.length() < 6 || s.length() > 48)
      return false;
    if (containsLowQualityToken(s))
      return false;
    String lower = s.toLowerCase();
    if (lower.contains("以上都对") || lower.contains("以上都错"))
      return false;
    return chineseRatio(s) >= 0.25;
  }

  private String clauseFromStatement(String statement) {
    String clean = sanitizeKnowledgeText(statement);
    String[] parts = clean.split("[，,:：]");
    for (String p : parts) {
      String item = p.trim();
      if (item.length() >= 8 && item.length() <= 44 && isLikelyNaturalSentence(item)) {
        return item.endsWith("。") ? item : item + "。";
      }
    }
    return shortSentence(clean);
  }

  private String buildAntiPatternOption(String topic, int seed) {
    List<String> templates = List.of(
        "在" + topic + "中跳过需求澄清，直接推进实施。",
        "在" + topic + "中只看最终结果，不做过程留痕。",
        "在" + topic + "中不设验收标准，完全依赖个人经验。",
        "在" + topic + "中先上线后补风险控制措施。");
    return templates.get(Math.floorMod(seed, templates.size()));
  }

  private double chineseRatio(String text) {
    if (text == null || text.isBlank())
      return 0;
    long len = text.length();
    long zh = text.chars().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN).count();
    return (double) zh / Math.max(1, len);
  }

  private int safeCount(Integer count) {
    return count == null || count < 0 ? 0 : count;
  }

  private String extractJsonObject(String raw) {
    int first = raw.indexOf('{');
    int last = raw.lastIndexOf('}');
    if (first >= 0 && last > first)
      return raw.substring(first, last + 1);
    return raw;
  }

  private ExamPaper findPublishedPaperByToken(String shareToken) {
    ExamPaper paper = examPaperRepository.findByShareToken(shareToken).orElseThrow(EntityNotFoundException::new);
    if (!Boolean.TRUE.equals(paper.getPublished()))
      throw new IllegalStateException("Paper is not published");
    return paper;
  }

  private ExamPaperResponse toPaperResponse(ExamPaper paper, List<ExamQuestion> questions) {
    return ExamPaperResponse.builder()
        .id(paper.getId())
        .knowledgeBaseId(paper.getKnowledgeBase().getId())
        .title(paper.getTitle())
        .instructions(paper.getInstructions())
        .totalScore(paper.getTotalScore())
        .createdAt(paper.getCreatedAt())
        .published(paper.getPublished())
        .shareToken(paper.getShareToken())
        .questions(questions.stream().map(this::toQuestionResponse).collect(Collectors.toList()))
        .build();
  }

  private ExamQuestionResponse toQuestionResponse(ExamQuestion q) {
    return ExamQuestionResponse.builder()
        .id(q.getId())
        .questionType(q.getQuestionType())
        .stem(q.getStem())
        .optionsJson(q.getOptionsJson())
        .score(q.getScore())
        .sortIndex(q.getSortIndex())
        .build();
  }

  private ExamSubmissionResponse toSubmissionResponse(ExamSubmission sub) {
    return ExamSubmissionResponse.builder()
        .id(sub.getId())
        .paperId(sub.getPaper().getId())
        .submitterName(sub.getSubmitterName())
        .score(sub.getScore())
        .aiFeedback(sub.getAiFeedback())
        .status(sub.getStatus())
        .submittedAt(sub.getSubmittedAt())
        .gradedAt(sub.getGradedAt())
        .build();
  }

  private List<ExamSubmissionResponse> rankSubmissions(List<ExamSubmission> submissions) {
    List<ExamSubmission> normalized = normalizeByRepeatStrategy(submissions);
    List<ExamSubmission> ordered = new ArrayList<>(normalized);
    ordered.sort((a, b) -> {
      BigDecimal sa = a.getScore() == null ? BigDecimal.valueOf(-1) : a.getScore();
      BigDecimal sb = b.getScore() == null ? BigDecimal.valueOf(-1) : b.getScore();
      int cmp = sb.compareTo(sa);
      if (cmp != 0)
        return cmp;
      return a.getSubmittedAt().compareTo(b.getSubmittedAt());
    });

    List<ExamSubmissionResponse> result = new ArrayList<>();
    int rank = 0;
    BigDecimal last = null;
    for (int i = 0; i < ordered.size(); i++) {
      ExamSubmission s = ordered.get(i);
      if (s.getScore() == null || last == null || s.getScore().compareTo(last) != 0)
        rank = i + 1;
      last = s.getScore();
      ExamSubmissionResponse r = toSubmissionResponse(s);
      r.setRank(rank);
      result.add(r);
    }
    return result;
  }

  private List<ExamSubmission> normalizeByRepeatStrategy(List<ExamSubmission> submissions) {
    if (repeatSubmissionStrategy == RepeatSubmissionStrategy.ALL)
      return submissions;
    Map<String, ExamSubmission> map = new HashMap<>();
    for (ExamSubmission s : submissions) {
      String key = ownerKey(s.getSubmitterId(), s.getSubmitterName());
      ExamSubmission cur = map.get(key);
      if (cur == null) {
        map.put(key, s);
        continue;
      }
      if (repeatSubmissionStrategy == RepeatSubmissionStrategy.LATEST) {
        if (s.getSubmittedAt().isAfter(cur.getSubmittedAt()))
          map.put(key, s);
      } else {
        BigDecimal cs = cur.getScore() == null ? BigDecimal.valueOf(-1) : cur.getScore();
        BigDecimal ns = s.getScore() == null ? BigDecimal.valueOf(-1) : s.getScore();
        if (ns.compareTo(cs) > 0)
          map.put(key, s);
      }
    }
    return new ArrayList<>(map.values());
  }

  private void deleteOtherOwnerSubmissions(List<ExamSubmission> ownerSubmissions, ExamSubmission keep) {
    List<ExamSubmission> toDelete = ownerSubmissions.stream().filter(s -> !s.getId().equals(keep.getId()))
        .collect(Collectors.toList());
    if (!toDelete.isEmpty())
      examSubmissionRepository.deleteAll(toDelete);
  }

  private void updateSubmission(ExamSubmission submission, Long submitterId, String submitterName,
      String answersJson, GradeResult grade, Instant now) {
    submission.setSubmitterId(submitterId);
    submission.setSubmitterName(submitterName);
    submission.setAnswersJson(answersJson);
    submission.setScore(grade.score);
    submission.setAiFeedback(grade.feedback);
    submission.setStatus(ExamSubmissionStatus.GRADED);
    submission.setSubmittedAt(now);
    submission.setGradedAt(now);
  }

  private String ownerKey(Long submitterId, String submitterName) {
    if (submitterId != null)
      return "U:" + submitterId;
    return "N:" + (submitterName == null ? "" : submitterName.trim().toLowerCase());
  }

  private RepeatSubmissionStrategy parseRepeatStrategy(String raw) {
    try {
      return RepeatSubmissionStrategy.valueOf(raw == null ? "ALL" : raw.trim().toUpperCase());
    } catch (Exception ex) {
      return RepeatSubmissionStrategy.ALL;
    }
  }

  private static class GeneratedQuestion {
    private final String type;
    private final String stem;
    private final String optionsJson;
    private final String answer;
    private final BigDecimal score;

    private GeneratedQuestion(String type, String stem, String optionsJson, String answer, BigDecimal score) {
      this.type = type;
      this.stem = stem;
      this.optionsJson = optionsJson;
      this.answer = answer;
      this.score = score;
    }
  }

  private static class GradeResult {
    private final BigDecimal score;
    private final String feedback;

    private GradeResult(BigDecimal score, String feedback) {
      this.score = score;
      this.feedback = feedback;
    }
  }

  private static class FactItem {
    private final String topic;
    private final String statement;

    private FactItem(String topic, String statement) {
      this.topic = topic;
      this.statement = statement;
    }
  }
}
