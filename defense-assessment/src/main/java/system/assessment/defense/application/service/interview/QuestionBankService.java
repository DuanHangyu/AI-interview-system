package system.assessment.defense.application.service.interview;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.assessment.defense.application.dto.QuestionDraftCmd;
import system.assessment.defense.application.dto.QuestionItemDTO;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 题库：教师维护预制题目；题目语音在保存后由后台任务预先合成，面试时直接读取存储，
 * 学生端答题链路上不再实时调用大模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private static final int MAX_QUESTIONS = 500;
    private static final int MAX_CONTENT_CHARS = 5000;
    private static final int MAX_DRAFT_COUNT = 30;

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
            String content = questions.get(i) == null ? null : questions.get(i).getContent();
            if (StringUtils.isBlank(content)) {
                throw new BusinessException(400, "第 " + (i + 1) + " 道题的内容不能为空");
            }
            if (content.length() > MAX_CONTENT_CHARS) {
                throw new BusinessException(400, "第 " + (i + 1) + " 道题内容过长");
            }
        }
        if (questions.size() < questionCount) {
            throw new BusinessException(400, "题库共 " + questions.size() + " 道题，少于每位学生的抽题数 " + questionCount);
        }
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
            String followUp = StringUtils.trimToNull(item.getFollowUpQuestion());
            AssessmentQuestionPO old = item.getId() == null ? null : existing.get(item.getId());
            if (old == null) {
                AssessmentQuestionPO po = AssessmentQuestionPO.builder()
                        .assessmentId(assessmentId)
                        .dimension(StringUtils.trimToNull(item.getDimension()))
                        .content(content)
                        .referenceAnswer(StringUtils.trimToNull(item.getReferenceAnswer()))
                        .followUpQuestion(followUp)
                        .sort(i)
                        .deleted(false)
                        .build();
                questionService.save(po);
                needAudio.add(po.getId());
                continue;
            }
            kept.add(old.getId());
            boolean contentChanged = !Objects.equals(old.getContent(), content);
            boolean followUpChanged = !Objects.equals(old.getFollowUpQuestion(), followUp);
            questionService.update(Wrappers.lambdaUpdate(AssessmentQuestionPO.class)
                    .set(AssessmentQuestionPO::getDimension, StringUtils.trimToNull(item.getDimension()))
                    .set(AssessmentQuestionPO::getContent, content)
                    .set(AssessmentQuestionPO::getReferenceAnswer, StringUtils.trimToNull(item.getReferenceAnswer()))
                    .set(AssessmentQuestionPO::getFollowUpQuestion, followUp)
                    .set(AssessmentQuestionPO::getSort, i)
                    .set(contentChanged, AssessmentQuestionPO::getAudioKey, null)
                    .set(followUpChanged, AssessmentQuestionPO::getFollowUpAudioKey, null)
                    .eq(AssessmentQuestionPO::getId, old.getId()));
            if (contentChanged || followUpChanged || old.getAudioKey() == null) {
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
     * 预生成一道题（及其追问）的播报音频。由 TTS_QUESTION 任务调用，可重复执行。
     */
    public void ensureAudio(Integer questionId) {
        AssessmentQuestionPO question = questionService.getById(questionId);
        if (question == null) {
            return;
        }
        if (question.getAudioKey() == null) {
            audioFor(question, false);
        }
        if (question.getFollowUpAudioKey() == null && StringUtils.isNotBlank(question.getFollowUpQuestion())) {
            audioFor(question, true);
        }
    }

    /**
     * 取题目播报音频：优先读预生成的文件，没有则现场合成并回写（同一题并发请求只合成一次）。
     *
     * @param expectedText 学生抽题时保存的题目文本快照；与题库当前内容不一致时（教师已修改题目）按快照现场合成，不回写
     */
    public byte[] questionAudio(Integer questionId, boolean followUp, String expectedText) {
        AssessmentQuestionPO question = questionId == null ? null : questionService.getById(questionId);
        String currentText = question == null ? null : (followUp ? question.getFollowUpQuestion() : question.getContent());
        if (question == null || !Objects.equals(StringUtils.trim(currentText), StringUtils.trim(expectedText))) {
            return synthesizeOnce("text:" + expectedText.hashCode(), () -> speechManager.synthesize(expectedText));
        }
        return audioFor(question, followUp);
    }

    private byte[] audioFor(AssessmentQuestionPO question, boolean followUp) {
        String key = followUp ? question.getFollowUpAudioKey() : question.getAudioKey();
        if (key != null) {
            try {
                return fileStorage.read(key);
            } catch (Exception e) {
                log.warn("读取题目音频失败，重新合成, questionId:{}, key:{}", question.getId(), key, e);
            }
        }
        String text = followUp ? question.getFollowUpQuestion() : question.getContent();
        SFunction<AssessmentQuestionPO, String> audioColumn =
                followUp ? AssessmentQuestionPO::getFollowUpAudioKey : AssessmentQuestionPO::getAudioKey;
        SFunction<AssessmentQuestionPO, String> textColumn =
                followUp ? AssessmentQuestionPO::getFollowUpQuestion : AssessmentQuestionPO::getContent;
        return synthesizeOnce("q:" + question.getId() + ":" + followUp, () -> {
            byte[] audio = speechManager.synthesize(text);
            String newKey = fileStorage.put("tts", "question.wav", audio, "audio/wav");
            // 只在题目文本未被修改时回写，避免把旧文本的音频写到新题目上
            questionService.update(Wrappers.lambdaUpdate(AssessmentQuestionPO.class)
                    .set(audioColumn, newKey)
                    .eq(AssessmentQuestionPO::getId, question.getId())
                    .eq(textColumn, text));
            return audio;
        });
    }

    private byte[] synthesizeOnce(String key, java.util.function.Supplier<byte[]> supplier) {
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
                .followUpQuestion(po.getFollowUpQuestion())
                .audioReady(po.getAudioKey() != null)
                .build();
    }
}
