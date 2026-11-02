package system.assessment.defense.infrastructure.common;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从考核标准文本中解析各维度满分，例如“技术实现（40分）：……”。
 */
public final class CriteriaParser {

    private static final Pattern DIMENSION = Pattern.compile(
            "([^\\s:：，,；;。.、()（）\\d]{2,20})\\s*[（(]\\s*(\\d{1,3})\\s*分\\s*[)）]");

    private CriteriaParser() {
    }

    public static Map<String, Integer> maxScores(String criteria) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(criteria)) {
            return result;
        }
        Matcher matcher = DIMENSION.matcher(criteria);
        while (matcher.find()) {
            result.putIfAbsent(matcher.group(1).trim(), Integer.parseInt(matcher.group(2)));
        }
        return result;
    }

    /**
     * 按维度名匹配满分：先精确匹配，再互相包含匹配（AI 返回的维度名可能略有出入）。
     */
    public static Integer maxScoreOf(Map<String, Integer> maxScores, String name) {
        if (StringUtils.isBlank(name) || maxScores.isEmpty()) {
            return null;
        }
        Integer exact = maxScores.get(name.trim());
        if (exact != null) {
            return exact;
        }
        return maxScores.entrySet().stream()
                .filter(e -> name.contains(e.getKey()) || e.getKey().contains(name.trim()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
