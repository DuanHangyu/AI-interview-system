-- ============================================================================
-- 升级已有数据库：预制题库面试 + 持久化任务队列 + 去除数字人
-- 适用于 V1（WebSocket 实时出题版本）的数据库。执行前请先备份。
-- 新建数据库请直接使用 scripts/local-db-schema.sql。
-- ============================================================================
SET NAMES utf8mb4;

-- 1. 题库
CREATE TABLE IF NOT EXISTS `assessment_question` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `assessment_id` INT NOT NULL,
  `dimension` VARCHAR(255) DEFAULT NULL COMMENT '考核维度，用于均衡抽题',
  `content` TEXT NOT NULL,
  `reference_answer` TEXT COMMENT '参考答案 / 评分要点，仅用于 AI 评分',
  `follow_up_question` TEXT COMMENT '预制追问，为空表示不追问',
  `sort` INT NOT NULL DEFAULT 0,
  `audio_key` VARCHAR(500) DEFAULT NULL COMMENT '题目播报音频存储 key',
  `follow_up_audio_key` VARCHAR(500) DEFAULT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_aq_assessment_id` (`assessment_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 持久化后台任务
CREATE TABLE IF NOT EXISTS `async_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_type` VARCHAR(32) NOT NULL,
  `biz_id` INT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待执行 1执行中 2成功 3失败',
  `attempts` INT NOT NULL DEFAULT 0,
  `max_attempts` INT NOT NULL DEFAULT 5,
  `next_run_time` DATETIME NOT NULL,
  `locked_until` DATETIME DEFAULT NULL,
  `owner` VARCHAR(64) DEFAULT NULL,
  `last_error` VARCHAR(1000) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task` (`task_type`, `biz_id`),
  KEY `idx_task_due` (`task_type`, `status`, `next_run_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 答题记录：抽题信息
ALTER TABLE `student_assessment_question_answer`
  ADD COLUMN `question_id` INT DEFAULT NULL COMMENT '抽中的题库题目 id' AFTER `student_id`,
  ADD COLUMN `sort_index` INT DEFAULT NULL COMMENT '本场面试中的题序，从 1 开始' AFTER `question_id`,
  ADD COLUMN `dimension` VARCHAR(255) DEFAULT NULL AFTER `sort_index`,
  ADD COLUMN `reference_answer` TEXT COMMENT '抽题时的参考答案快照' AFTER `dimension`;
-- 旧数据按 id 顺序补齐题序，保证唯一索引可以建立
UPDATE `student_assessment_question_answer` a
  JOIN (SELECT id, ROW_NUMBER() OVER (PARTITION BY student_id, assessment_id ORDER BY id) AS rn
        FROM `student_assessment_question_answer`) t ON a.id = t.id
  SET a.sort_index = t.rn
  WHERE a.sort_index IS NULL;
ALTER TABLE `student_assessment_question_answer`
  ADD UNIQUE KEY `uk_saqa_student_assessment_sort` (`student_id`, `assessment_id`, `sort_index`);

-- 4. 考核记录：面试开始时间 + 每个学生每场考核唯一
ALTER TABLE `student_assessment_record`
  ADD COLUMN `start_time` DATETIME DEFAULT NULL COMMENT '面试开始时间，为空表示未开始' AFTER `defense_time`;
-- 已有记录视为已开始（进行中的旧面试需要重新开始）
UPDATE `student_assessment_record` SET `start_time` = COALESCE(`start_defense_time`, `create_time`, NOW())
  WHERE `start_time` IS NULL AND `state` IN (1, 2);
-- 如有重复记录（同一学生同一考核多行），需先人工清理后再执行下一句：
--   SELECT student_id, assessment_id, COUNT(*) FROM student_assessment_record GROUP BY 1, 2 HAVING COUNT(*) > 1;
ALTER TABLE `student_assessment_record`
  ADD UNIQUE KEY `uk_sar_student_assessment` (`student_id`, `assessment_id`);

-- 5. 时间字段自动维护（旧代码从未写入这些字段）
ALTER TABLE `student_assessment_record`
  MODIFY `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  MODIFY `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE `student_assessment_question_answer`
  MODIFY `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP;

-- 6. 移除数字人与无用字段/表
ALTER TABLE `assessment` DROP COLUMN `digital_human_id`;
ALTER TABLE `user` DROP COLUMN `ability_advantage_analysis`, DROP COLUMN `development_potential_assessment`;
DROP TABLE IF EXISTS `digital_human`;
DROP TABLE IF EXISTS `lock_key`;

-- 7. 旧版本里的题目都是实时生成的，没有题库；需要提问的考核请教师在后台补充题库后再开放给学生。
