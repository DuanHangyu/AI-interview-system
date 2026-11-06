package system.assessment.defense.infrastructure.common;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分模型偶尔在中文点评里夹杂英文单词（如“学生 firmly 坚持人民立场”）。
 * 把夹在中文之间的常见英文词换成中文；AI、WTO 这类专有缩写和不在词表里的词保持原样。
 * 作用于模型返回的整段 JSON 文本：JSON 的键名两侧是引号而不是中文，不会被替换。
 */
public final class StrayEnglishFixer {

    private static final Map<String, String> WORDS = new LinkedHashMap<>();

    static {
        WORDS.put("political economy", "政治经济学");
        WORDS.put("critical thinking", "批判性思维");
        WORDS.put("firmly", "坚定地");
        WORDS.put("explicitly", "明确地");
        WORDS.put("explicit", "明确");
        WORDS.put("clearly", "清楚地");
        WORDS.put("deeply", "深刻地");
        WORDS.put("strongly", "有力地");
        WORDS.put("effectively", "有效地");
        WORDS.put("actively", "积极地");
        WORDS.put("accurately", "准确地");
        WORDS.put("fully", "充分地");
        WORDS.put("specifically", "具体地");
        WORDS.put("especially", "尤其");
        WORDS.put("however", "但是");
        WORDS.put("therefore", "因此");
        WORDS.put("moreover", "此外");
        WORDS.put("solid", "扎实");
        WORDS.put("insight", "见解");
        WORDS.put("logic", "逻辑");
    }

    /** 英文词 + 可选的中文括注，如 “explicitly（明确地）” */
    private static final Pattern WORD = Pattern.compile(
            "[ \\t]?\\b(" + String.join("|", WORDS.keySet()) + ")\\b[ \\t]?(（[^（）\"]{1,12}）)?",
            Pattern.CASE_INSENSITIVE);

    private StrayEnglishFixer() {
    }

    public static String fix(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher m = WORD.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        while (m.find()) {
            boolean inChinese = isChinese(text, m.start() - 1) || isChinese(text, m.end());
            String replacement = m.group(0);
            if (inChinese) {
                String gloss = m.group(2);
                replacement = gloss != null ? gloss.substring(1, gloss.length() - 1)
                        : WORDS.get(m.group(1).toLowerCase(Locale.ROOT));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isChinese(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return false;
        }
        char c = text.charAt(index);
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN || "，。、；：“”‘’（）！？".indexOf(c) >= 0;
    }
}
