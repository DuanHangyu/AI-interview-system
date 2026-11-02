package system.assessment.defense.application.service.interview;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.assessment.defense.application.dto.FollowUpItemDTO;
import system.assessment.defense.application.dto.QuestionDraftCmd;
import system.assessment.defense.application.dto.QuestionItemDTO;
import system.assessment.defense.application.dto.QuestionRubric;
import system.assessment.defense.application.manage.AiManager;
import system.assessment.defense.application.manage.SpeechManager;
import system.assessment.defense.application.service.AssessmentMaterialService;
import system.assessment.defense.application.task.TaskQueue;
import system.assessment.defense.application.task.TaskTypes;
import system.assessment.defense.infrastructure.exception.BusinessException;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentQuestionPO;
import system.assessment.defense.infrastructure.repository.dao.service.AssessmentQuestionService;
import system.assessment.defense.infrastructure.storage.FileStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 题库：教师维护预制题目（含评分标准和追问）；题目与追问语音在保存后由后台任务预先合成，面试时直接读取存储，
 * 学生端答题链路上不再实时调用大模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private static final int MAX_QUESTIONS = 500;
    private static final int MAX_CONTENT_CHARS = 5000;
    private static final int MAX_DRAFT_COUNT = 30;
    private static final int MAX_FOLLOW_UPS = 10;

    private final AssessmentQuestionService questionService;
    private final AssessmentMaterialService materialService;
    private final AiManager aiManager;
    private final SpeechManager speechManager;
    private final FileStorage fileStorage;
    private final TaskQueue taskQueue;

    /**
     * 同一段语音同一时刻只合成一次（多个学生同时请求同一道还没预生成音频的题）
     */
    private final Map<String, CompletableFuture<byte[]>> inflightAudio = new ConcurrentHashMap<>();

    public static void validate(Boolean questionEnabled, Integer questionCount, List<QuestionItemDTO> items) {
        if (!Objects.equals(questionEnabled, true)) {
            return;
        }
        List<QuestionItemDTO> questions = items == null ? List.of() : items;
        if (questionCount == null || questionCount <= 0) {
            throw new BusinessException(400, "请设置每位学生的抽题数");
        }
        if (questions.size() > MAX_QUESTIONS) {
            throw new BusinessException(400, "题库最多 " + MAX_QUESTIONS + " 道题");
        }
        for (int i = 0; i < questions.size(); i++) {
            QuestionItemDTO question = questions.get(i);
            String content = question == null ? null : question.getContent();
            if (StringUtils.isBlank(content)) {
                throw new BusinessException(400, "第 " + (i + 1) + " 道题的内容不能为空");
            }
            if (content.length() > MAX_CONTENT_CHARS) {
                throw new BusinessException(400, "第 " + (i + 1) + " 道题内容过长");
            }
            List<FollowUpItemDTO> followUps = normalizeFollowUps(question.getFollowUps());
            if (followUps.size() > MAX_FOLLOW_UPS) {
                throw new BusinessException(400, "第 " + (i + 1) + " 道题最多 " + MAX_FOLLOW_UPS + " 个追问");
            }
            if (followUps.stream().anyMatch(f -> f.getContent().length() > MAX_CONTENT_CHARS)) {
                throw new BusinessException(400, "第 " + (i + 1) + " 道题的追问内容过长");
            }
            validateRubric(i + 1, question.getRubric());
        }
        if (questions.size() < questionCount) {
            throw new BusinessException(400, "题库共 " + questions.size() + " 道题，少于每位学生的抽题数 " + questionCount);
        }
    }

    private static void validateRubric(int no, QuestionRubric rubric) {
        if (rubric == null || rubric.isEmpty()) {
            return;
        }
        Set<String> names = new HashSet<>();
        for (QuestionRubric.Dimension d : rubric.getDimensions()) {
            String name = d == null ? null : StringUtils.trimToNull(d.getName());
            if (name == null) {
                throw new BusinessException(400, "第 " + no + " 道题的评分维度名称不能为空");
            }
            if (!names.add(name)) {
                throw new BusinessException(400, "第 " + no + " 道题的评分维度“" + name + "”重复");
            }
            if (d.getMaxScore() == null || d.getMaxScore() <= 0 || d.getMaxScore() > 100) {
                throw new BusinessException(400, "第 " + no + " 道题“" + name + "”的满分应在 1～100 之间");
            }
        }
    }

    /**
     * 题目带评分标准时，总分由评分标准决定（每题满分 × 抽题数）。要求所有题目都有评分标准且满分一致，
     * 否则不同学生抽到不同的题，总分就不可比。
     */
    public static void applyRubricTotal(Consumer<Integer> totalScoreSetter, Integer questionCount,
                                        List<QuestionItemDTO> items) {
        List<QuestionItemDTO> questions = items == null ? List.of() : items;
        long withRubric = questions.stream().filter(q -> q.getRubric() != null && !q.getRubric().isEmpty()).count();
        if (withRubric == 0) {
            return;
        }
        if (withRubric != questions.size()) {
            throw new BusinessException(400, "部分题目设置了评分标准、部分没有：请为所有题目设置评分标准，或全部清空改用整体考核标准");
        }
        Set<Integer> totals = questions.stream().map(q -> q.getRubric().totalScore()).collect(Collectors.toSet());
        if (totals.size() > 1) {
            throw new BusinessException(400, "各题评分标准的满分不一致" + totals + "，请调整为相同满分");
        }
        totalScoreSetter.accept(totals.iterator().next() * (questionCount == null ? 1 : questionCount));
    }

    static List<FollowUpItemDTO> normalizeFollowUps(List<FollowUpItemDTO> followUps) {
        if (followUps == null) {
            return List.of();
        }
        return followUps.stream()
                .filter(f -> f != null && StringUtils.isNotBlank(f.getContent()))
                .map(f -> FollowUpItemDTO.builder()
                        .content(StringUtils.trim(f.getContent()))
                        .intent(StringUtils.trimToNull(f.getIntent()))
                        .build())
                .toList();
    }

    /**
     * 解析题库中存储的追问列表（含音频 key）
     */
    public static List<FollowUpItemDTO> parseFollowUps(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        return JSONUtil.toList(json, FollowUpItemDTO.class).stream()
                .filter(f -> f != null && StringUtils.isNotBlank(f.getContent()))
                .toList();
    }

    /**
     * 追问列表转 JSON：内容没变的追问沿用已生成的播报音频
     */
    private static String followUpsJson(List<FollowUpItemDTO> incoming, String oldJson) {
        List<FollowUpItemDTO> items = normalizeFollowUps(incoming);
        if (items.isEmpty()) {
            return null;
        }
        Map<String, String> oldAudio = parseFollowUps(oldJson).stream()
                .filter(f -> f.getAudioKey() != null)
                .collect(Collectors.toMap(FollowUpItemDTO::getContent, FollowUpItemDTO::getAudioKey, (a, b) -> a));
        items.forEach(f -> f.setAudioKey(oldAudio.get(f.getContent())));
        return JSONUtil.toJsonStr(items);
    }

    private static String rubricJson(QuestionRubric rubric) {
        if (rubric == null || rubric.isEmpty()) {
            return null;
        }
        rubric.getDimensions().forEach(d -> d.setName(StringUtils.trim(d.getName())));
        rubric.setNotes(StringUtils.trimToNull(rubric.getNotes()));
        return JSONUtil.toJsonStr(rubric);
    }

    /**
     * 以传入列表为准覆盖考核的题库：有 id 的更新、无 id 的新增、未出现的删除（逻辑删除，已抽中的答题记录保存了题目快照）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveQuestions(Integer assessmentId, List<QuestionItemDTO> items) {
        List<QuestionItemDTO> incoming = items == null ? List.of() : items;
        Map<Integer, AssessmentQuestionPO> existing = questionService.listByAssessmentId(assessmentId).stream()
                .collect(Collectors.toMap(AssessmentQuestionPO::getId, Function.identity()));
        List<Integer> needAudio = new ArrayList<>();
        List<Integer> kept = new ArrayList<>();
        for (int i = 0; i < incoming.size(); i++) {
            QuestionItemDTO item = incoming.get(i);
            String content = StringUtils.trim(item.getContent());
            AssessmentQuestionPO old = item.getId() == null ? null : existing.get(item.getId());
            String followUps = followUpsJson(item.getFollowUps(), old == null ? null : old.getFollowUps());
            String rubric = rubricJson(item.getRubric());
            if (old == null) {
                AssessmentQuestionPO po = AssessmentQuestionPO.builder()
                        .assessmentId(assessmentId)
                        .dimension(StringUtils.trimToNull(item.getDimension()))
                        .content(content)
                        .referenceAnswer(StringUtils.trimToNull(item.getReferenceAnswer()))
                        .rubric(rubric)
                        .followUps(followUps)
                        .sort(i)
                        .deleted(false)
                        .build();
                questionService.save(po);
                needAudio.add(po.getId());
                continue;
            }
            kept.add(old.getId());
            boolean contentChanged = !Objects.equals(old.getContent(), content);
            boolean followUpAudioMissing = parseFollowUps(followUps).stream().anyMatch(f -> f.getAudioKey() == null);
            questionService.update(Wrappers.lambdaUpdate(AssessmentQuestionPO.class)
                    .set(AssessmentQuestionPO::getDimension, StringUtils.trimToNull(item.getDimension()))
                    .set(AssessmentQuestionPO::getContent, content)
                    .set(AssessmentQuestionPO::getReferenceAnswer, StringUtils.trimToNull(item.getReferenceAnswer()))
                    .set(AssessmentQuestionPO::getRubric, rubric)
                    .set(AssessmentQuestionPO::getFollowUps, followUps)
                    .set(AssessmentQuestionPO::getSort, i)
                    .set(contentChanged, AssessmentQuestionPO::getAudioKey, null)
                    .eq(AssessmentQuestionPO::getId, old.getId()));
            if (contentChanged || followUpAudioMissing || old.getAudioKey() == null) {
                needAudio.add(old.getId());
            }
        }
        List<Integer> removed = existing.keySet().stream().filter(id -> !kept.contains(id)).toList();
        if (!removed.isEmpty()) {
            questionService.removeByIds(removed);
        }
        needAudio.forEach(id -> taskQueue.enqueueAfterCommit(TaskTypes.TTS_QUESTION, id));
    }

    public List<QuestionItemDTO> listQuestions(Integer assessmentId) {
        return questionService.listByAssessmentId(assessmentId).stream().map(QuestionBankService::toDTO).toList();
    }

    public Map<Integer, List<QuestionItemDTO>> listQuestions(Collection<Integer> assessmentIds) {
        return questionService.mapByAssessmentIds(assessmentIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().map(QuestionBankService::toDTO).toList()));
    }

    public void deleteByAssessment(Integer assessmentId) {
        questionService.remove(Wrappers.lambdaQuery(AssessmentQuestionPO.class)
                .eq(AssessmentQuestionPO::getAssessmentId, assessmentId));
    }

    /**
     * AI 生成题库草稿，供教师编辑后保存。
     */
    public List<QuestionItemDTO> draft(QuestionDraftCmd cmd) {
        int count = Math.max(1, Math.min(MAX_DRAFT_COUNT, cmd.getCount() == null ? 10 : cmd.getCount()));
        String material = CollectionUtils.isEmpty(cmd.getAssessmentFiles())
                ? "" : materialService.parseMaterialContext(cmd.getAssessmentFiles());
        List<QuestionItemDTO> draft = aiManager.generateQuestionDraft(cmd.getTheme(), cmd.getAssessmentRequirements(),
                cmd.getAssessmentCriteria(), material, count);
        if (draft.isEmpty()) {
            throw new BusinessException(500, "AI 未能生成题目，请调整考核信息后重试");
        }
        return draft;
    }

    /**
     * 预生成一道题及其全部追问的播报音频。由 TTS_QUESTION 任务调用，可重复执行。
     */
    public void ensureAudio(Integer questionId) {
        AssessmentQuestionPO question = questionService.getById(questionId);
        if (question == null) {
            return;
        }
        if (question.getAudioKey() == null) {
            audioFor(question);
        }
        for (FollowUpItemDTO followUp : parseFollowUps(question.getFollowUps())) {
            if (followUp.getAudioKey() == null) {
                followUpAudio(question.getId(), followUp.getContent());
            }
        }
    }

    /**
     * 取题目播报音频：优先读预生成的文件，没有则现场合成并回写（同一题并发请求只合成一次）。
     *
     * @param expectedText 学生抽题时保存的题目文本快照；与题库当前内容不一致时（教师已修改题目）按快照现场合成，不回写
     */
    public byte[] questionAudio(Integer questionId, String expectedText) {
        AssessmentQuestionPO question = questionId == null ? null : questionService.getById(questionId);
        if (question == null || !Objects.equals(StringUtils.trim(question.getContent()), StringUtils.trim(expectedText))) {
            return synthesizeText(expectedText);
        }
        return audioFor(question);
    }

    /**
     * 取追问播报音频：按追问文本在题库中找到对应的追问；找不到（教师已修改）时按快照现场合成，不回写。
     */
    public byte[] followUpAudio(Integer questionId, String expectedText) {
        AssessmentQuestionPO question = questionId == null ? null : questionService.getById(questionId);
        String text = StringUtils.trim(expectedText);
        FollowUpItemDTO followUp = question == null ? null : parseFollowUps(question.getFollowUps()).stream()
                .filter(f -> Objects.equals(f.getContent(), text))
                .findFirst()
                .orElse(null);
        if (followUp == null) {
            return synthesizeText(expectedText);
        }
        byte[] stored = readQuietly(followUp.getAudioKey(), questionId);
        if (stored != null) {
            return stored;
        }
        return synthesizeOnce("f:" + questionId + ":" + text.hashCode(), () -> {
            byte[] audio = speechManager.synthesize(text);
            String newKey = fileStorage.put("tts", "question.wav", audio, "audio/wav");
            saveFollowUpAudioKey(questionId, text, newKey);
            return audio;
        });
    }

    /**
     * 把追问音频 key 写回追问列表；以读到的 JSON 作为更新条件，期间教师改过题目则放弃回写
     */
    private void saveFollowUpAudioKey(Integer questionId, String text, String key) {
        AssessmentQuestionPO latest = questionService.getById(questionId);
        if (latest == null || latest.getFollowUps() == null) {
            return;
        }
        List<FollowUpItemDTO> items = parseFollowUps(latest.getFollowUps());
        boolean hit = false;
        for (FollowUpItemDTO item : items) {
            if (Objects.equals(item.getContent(), text)) {
                item.setAudioKey(key);
                hit = true;
            }
        }
        if (hit) {
            questionService.update(Wrappers.lambdaUpdate(AssessmentQuestionPO.class)
                    .set(AssessmentQuestionPO::getFollowUps, JSONUtil.toJsonStr(items))
                    .eq(AssessmentQuestionPO::getId, questionId)
                    .eq(AssessmentQuestionPO::getFollowUps, latest.getFollowUps()));
        }
    }

    private byte[] audioFor(AssessmentQuestionPO question) {
        byte[] stored = readQuietly(question.getAudioKey(), question.getId());
        if (stored != null) {
            return stored;
        }
        String text = question.getContent();
        return synthesizeOnce("q:" + question.getId(), () -> {
            byte[] audio = speechManager.synthesize(text);
            String newKey = fileStorage.put("tts", "question.wav", audio, "audio/wav");
            // 只在题目文本未被修改时回写，避免把旧文本的音频写到新题目上
            questionService.update(Wrappers.lambdaUpdate(AssessmentQuestionPO.class)
                    .set(AssessmentQuestionPO::getAudioKey, newKey)
                    .eq(AssessmentQuestionPO::getId, question.getId())
                    .eq(AssessmentQuestionPO::getContent, text));
            return audio;
        });
    }

    private byte[] readQuietly(String key, Integer questionId) {
        if (key == null) {
            return null;
        }
        try {
            return fileStorage.read(key);
        } catch (Exception e) {
            log.warn("读取题目音频失败，重新合成, questionId:{}, key:{}", questionId, key, e);
            return null;
        }
    }

    private byte[] synthesizeText(String text) {
        return synthesizeOnce("text:" + text.hashCode(), () -> speechManager.synthesize(text));
    }

    private byte[] synthesizeOnce(String key, Supplier<byte[]> supplier) {
        CompletableFuture<byte[]> mine = new CompletableFuture<>();
        CompletableFuture<byte[]> existing = inflightAudio.putIfAbsent(key, mine);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                throw e.getCause() instanceof RuntimeException re ? re : e;
            }
        }
        try {
            byte[] audio = supplier.get();
            mine.complete(audio);
            return audio;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inflightAudio.remove(key, mine);
        }
    }

    private static QuestionItemDTO toDTO(AssessmentQuestionPO po) {
        return QuestionItemDTO.builder()
                .id(po.getId())
                .dimension(po.getDimension())
                .content(po.getContent())
                .referenceAnswer(po.getReferenceAnswer())
                .rubric(QuestionRubric.parse(po.getRubric()))
                .followUps(parseFollowUps(po.getFollowUps()).stream()
                        .map(f -> FollowUpItemDTO.builder()
                                .content(f.getContent())
                                .intent(f.getIntent())
                                .audioReady(f.getAudioKey() != null)
                                .build())
                        .toList())
                .audioReady(po.getAudioKey() != null)
                .build();
    }
}
