package system.assessment.defense.application.service.interview;

import org.apache.commons.lang3.StringUtils;
import system.assessment.defense.infrastructure.repository.dao.po.AssessmentQuestionPO;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 从题库中为学生随机抽题。
 * <p>
 * 按维度均衡：先把每个维度内部打乱，再在打乱顺序的维度之间轮流取题，
 * 保证抽中的题目覆盖尽可能多的考核维度，且同一维度的题目不会扎堆出现。
 */
public final class QuestionDrawer {

    private static final String DEFAULT_DIMENSION = "";

    private QuestionDrawer() {
    }

    public static List<AssessmentQuestionPO> draw(List<AssessmentQuestionPO> bank, int count, Random random) {
        if (bank == null || bank.isEmpty() || count <= 0) {
            return List.of();
        }
        Map<String, List<AssessmentQuestionPO>> byDimension = new LinkedHashMap<>();
        for (AssessmentQuestionPO question : bank) {
            String dimension = StringUtils.defaultIfBlank(StringUtils.trim(question.getDimension()), DEFAULT_DIMENSION);
            byDimension.computeIfAbsent(dimension, k -> new ArrayList<>()).add(question);
        }
        List<Deque<AssessmentQuestionPO>> groups = new ArrayList<>();
        for (List<AssessmentQuestionPO> group : byDimension.values()) {
            List<AssessmentQuestionPO> shuffled = new ArrayList<>(group);
            Collections.shuffle(shuffled, random);
            groups.add(new ArrayDeque<>(shuffled));
        }
        Collections.shuffle(groups, random);

        int target = Math.min(count, bank.size());
        List<AssessmentQuestionPO> picked = new ArrayList<>(target);
        while (picked.size() < target) {
            for (Deque<AssessmentQuestionPO> group : groups) {
                if (picked.size() >= target) {
                    break;
                }
                AssessmentQuestionPO next = group.pollFirst();
                if (next != null) {
                    picked.add(next);
                }
            }
        }
        return picked;
    }

    /**
     * 抽取追问：count 为空、不大于 0 或不少于追问总数时全部提问；否则随机抽取 count 个，保持教师设定的先后顺序
     * （追问通常是递进的）。
     */
    public static <T> List<T> pickFollowUps(List<T> followUps, Integer count, Random random) {
        if (followUps == null || followUps.isEmpty()) {
            return List.of();
        }
        if (count == null || count <= 0 || count >= followUps.size()) {
            return List.copyOf(followUps);
        }
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < followUps.size(); i++) {
            indexes.add(i);
        }
        Collections.shuffle(indexes, random);
        return indexes.subList(0, count).stream().sorted().map(followUps::get).toList();
    }
}
