package system.assessment.defense.infrastructure;

import org.junit.jupiter.api.Test;
import system.assessment.defense.infrastructure.common.StrayEnglishFixer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrayEnglishFixerTest {

    @Test
    void replacesWordsBetweenChinese() {
        assertEquals("学生坚定地坚持人民立场", StrayEnglishFixer.fix("学生 firmly 坚持人民立场"));
        assertEquals("批判性强，坚定地站稳人民立场", StrayEnglishFixer.fix("批判性强， firmly 站稳人民立场"));
        assertEquals("未做更深层次的政治经济学分析", StrayEnglishFixer.fix("未做更深层次的 political economy 分析"));
    }

    @Test
    void prefersChineseGloss() {
        assertEquals("在价值维度中，未明确地结合自身专业", StrayEnglishFixer.fix("在价值维度中，未 explicitly（明确地）结合自身专业"));
    }

    @Test
    void keepsAbbreviationsJsonKeysAndEnglishContext() {
        assertEquals("结合WTO参与、AI发展分析", StrayEnglishFixer.fix("结合WTO参与、AI发展分析"));
        assertEquals("{\"logic\": \"逻辑清楚\"}", StrayEnglishFixer.fix("{\"logic\": \"逻辑清楚\"}"));
        assertEquals("solid work", StrayEnglishFixer.fix("solid work"));
    }
}
