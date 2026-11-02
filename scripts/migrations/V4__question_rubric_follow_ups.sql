-- V4：每道题独立的评分标准（多维度、分档）+ 每道题多个追问
SET NAMES utf8mb4;

-- 1. 题库：评分标准与追问列表（JSON）
ALTER TABLE `assessment_question`
  ADD COLUMN `rubric` TEXT NULL COMMENT '本题评分标准 JSON：维度、满分、考察点、分档表现、评分说明' AFTER `reference_answer`,
  ADD COLUMN `follow_ups` TEXT NULL COMMENT '追问列表 JSON：[{content, intent, audioKey}]' AFTER `rubric`;
UPDATE `assessment_question`
  SET `follow_ups` = JSON_ARRAY(JSON_OBJECT('content', TRIM(`follow_up_question`), 'audioKey', `follow_up_audio_key`))
  WHERE `follow_ups` IS NULL AND `follow_up_question` IS NOT NULL AND TRIM(`follow_up_question`) <> '';

-- 2. 考核：每道题追问几个（为空表示题目里的追问全部提问）
ALTER TABLE `assessment`
  ADD COLUMN `follow_up_count` INT NULL COMMENT '每道题的追问个数，少于题目追问数时随机抽取' AFTER `follow_up`;

-- 3. 答题记录：追问作为独立的行（parent_id 指向主问题行），主问题行保存评分标准快照
ALTER TABLE `student_assessment_question_answer`
  ADD COLUMN `parent_id` INT NULL COMMENT '追问所属的主问题行 id，主问题为空' AFTER `question_id`,
  ADD COLUMN `follow_index` INT NULL COMMENT '追问序号，从 1 开始' AFTER `parent_id`,
  ADD COLUMN `follow_intent` TEXT NULL COMMENT '追问的考察意图快照' AFTER `follow_index`,
  ADD COLUMN `rubric` TEXT NULL COMMENT '抽题时的评分标准快照' AFTER `reference_answer`,
  ADD UNIQUE KEY `uk_saqa_parent_follow` (`parent_id`, `follow_index`);
-- 旧数据的单个追问迁移为追问行
INSERT IGNORE INTO `student_assessment_question_answer`
  (`assessment_id`, `student_id`, `question_id`, `parent_id`, `follow_index`, `question`, `answer`, `answer_voice`, `create_time`)
SELECT `assessment_id`, `student_id`, `question_id`, `id`, 1, `follow_question`, `follow_answer`, `follow_answer_voice`, `create_time`
  FROM `student_assessment_question_answer`
  WHERE `parent_id` IS NULL AND `follow_question` IS NOT NULL AND TRIM(`follow_question`) <> '';
