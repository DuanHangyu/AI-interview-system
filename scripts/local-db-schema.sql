-- ============================================================================
-- Local development schema for the AI Interview System (defense-assessment).
-- Matches the MyBatis-Plus PO entities.
-- NOTE: This is a fresh local DB (data was disposable dev data). Column types
-- are inferred from Java field types; nullable/length/index choices are
-- reasonable defaults. If a flow reports a column mismatch, fix here & re-apply.
-- Apply on the `defense_test` database.
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- user (STUDENT=0, TEACHER=1, ADMIN=2) ----------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) DEFAULT NULL,
  `account` VARCHAR(255) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `phone` VARCHAR(32) DEFAULT NULL,
  `type` INT DEFAULT NULL COMMENT '0=student,1=teacher,2=admin',
  `school_class` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- assessment ----------
DROP TABLE IF EXISTS `assessment`;
CREATE TABLE `assessment` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `theme` VARCHAR(500) DEFAULT NULL,
  `assessment_requirements` TEXT,
  `assessment_criteria` TEXT,
  `total_score` INT DEFAULT NULL,
  `pass_score` INT DEFAULT NULL,
  `duration` INT DEFAULT NULL,
  `question_count` INT DEFAULT NULL,
  `answer_time` INT DEFAULT NULL,
  `follow_up` TINYINT(1) DEFAULT 0,
  `follow_up_count` INT DEFAULT NULL COMMENT '每道题的追问个数，少于题目追问数时随机抽取',
  `follow_up_prompt` TEXT,
  `follow_up_standards` TEXT,
  `assessment_files` TEXT,
  `defense` TINYINT(1) DEFAULT 0,
  `question` TINYINT(1) DEFAULT 0,
  `show_result` TINYINT(1) DEFAULT 0,
  `create_id` INT DEFAULT NULL,
  `need_appoint` TINYINT(1) DEFAULT 0,
  `assessment_fail_punish` TINYINT(1) DEFAULT 0,
  `reschedule_appoint_time` DATETIME DEFAULT NULL,
  `last_appoint_time` DATETIME DEFAULT NULL,
  `deleted` TINYINT(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_assessment_create_id` (`create_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- assessment_appointment_setting ----------
DROP TABLE IF EXISTS `assessment_appointment_setting`;
CREATE TABLE `assessment_appointment_setting` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `assessment_id` INT DEFAULT NULL,
  `time_period` DATETIME DEFAULT NULL COMMENT '场次开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '场次结束时间',
  `participant_limit` INT DEFAULT NULL,
  `location` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_aas_assessment_id` (`assessment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- assessment_invite ----------
DROP TABLE IF EXISTS `assessment_invite`;
CREATE TABLE `assessment_invite` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `assessment_id` INT DEFAULT NULL,
  `invitee_teacher_id` INT DEFAULT NULL,
  `inviter_teacher_id` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_assessment_id` (`assessment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- assessment_student_relation ----------
DROP TABLE IF EXISTS `assessment_student_relation`;
CREATE TABLE `assessment_student_relation` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `assessment_id` INT DEFAULT NULL,
  `student_id` INT DEFAULT NULL,
  `relation_type` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_asr_assessment_id` (`assessment_id`),
  KEY `idx_asr_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- project_type ----------
DROP TABLE IF EXISTS `project_type`;
CREATE TABLE `project_type` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `project_type_key` VARCHAR(255) DEFAULT NULL,
  `project_type` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- student_assessment_appointment ----------
DROP TABLE IF EXISTS `student_assessment_appointment`;
CREATE TABLE `student_assessment_appointment` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `student_id` INT DEFAULT NULL,
  `assessment_id` INT DEFAULT NULL,
  `setting_id` INT DEFAULT NULL COMMENT '所在场次 id',
  `appointment_time` DATETIME DEFAULT NULL,
  `time_period` DATETIME DEFAULT NULL,
  `state` INT DEFAULT NULL,
  `punish_state` INT DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_saa_student_id` (`student_id`, `assessment_id`),
  KEY `idx_saa_assessment_id` (`assessment_id`),
  KEY `idx_saa_assessment_student` (`assessment_id`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- student_assessment_question_answer ----------
DROP TABLE IF EXISTS `student_assessment_question_answer`;
CREATE TABLE `student_assessment_question_answer` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `assessment_id` INT DEFAULT NULL,
  `student_id` INT DEFAULT NULL,
  `question_id` INT DEFAULT NULL COMMENT '抽中的题库题目 id',
  `parent_id` INT DEFAULT NULL COMMENT '追问所属的主问题行 id，主问题为空',
  `follow_index` INT DEFAULT NULL COMMENT '追问序号，从 1 开始',
  `follow_intent` TEXT COMMENT '追问的考察意图快照',
  `sort_index` INT DEFAULT NULL COMMENT '本场面试中的题序，从 1 开始',
  `dimension` VARCHAR(255) DEFAULT NULL,
  `reference_answer` TEXT COMMENT '抽题时的参考答案快照',
  `rubric` TEXT COMMENT '抽题时的评分标准快照',
  `question` TEXT,
  `answer` TEXT,
  `answer_voice` VARCHAR(1000) DEFAULT NULL,
  `follow_question` TEXT,
  `follow_answer` TEXT,
  `follow_answer_voice` VARCHAR(1000) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saqa_student_assessment_sort` (`student_id`, `assessment_id`, `sort_index`),
  UNIQUE KEY `uk_saqa_parent_follow` (`parent_id`, `follow_index`),
  KEY `idx_saqa_assessment_id` (`assessment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- student_assessment_record ----------
DROP TABLE IF EXISTS `student_assessment_record`;
CREATE TABLE `student_assessment_record` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `assessment_id` INT DEFAULT NULL,
  `student_id` INT DEFAULT NULL,
  `defense_file` VARCHAR(1000) DEFAULT NULL,
  `defense_content` TEXT,
  `defense_voice` VARCHAR(1000) DEFAULT NULL,
  `score` INT DEFAULT NULL,
  `check_score` INT DEFAULT NULL,
  `defense_time` INT DEFAULT NULL,
  `start_time` DATETIME DEFAULT NULL COMMENT '面试开始时间，为空表示未开始',
  `start_defense_time` DATETIME DEFAULT NULL,
  `end_defense_time` DATETIME DEFAULT NULL,
  `defense_result` TEXT,
  `state` INT DEFAULT NULL,
  `re_analysis` TINYINT(1) DEFAULT 0,
  `teacher_review` TEXT NULL COMMENT '教师复核结果 JSON',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sar_student_assessment` (`student_id`, `assessment_id`),
  KEY `idx_sar_assessment_id` (`assessment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------- assessment_question (题库) ----------
DROP TABLE IF EXISTS `assessment_question`;
CREATE TABLE `assessment_question` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `assessment_id` INT NOT NULL,
  `dimension` VARCHAR(255) DEFAULT NULL COMMENT '考核维度，用于均衡抽题',
  `content` TEXT NOT NULL,
  `reference_answer` TEXT COMMENT '参考答案 / 评分要点，仅用于 AI 评分',
  `rubric` TEXT COMMENT '本题评分标准 JSON：维度、满分、考察点、分档表现、评分说明',
  `follow_ups` TEXT COMMENT '追问列表 JSON：[{content, intent, audioKey}]',
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

-- ---------- async_task (持久化后台任务) ----------
DROP TABLE IF EXISTS `async_task`;
CREATE TABLE `async_task` (
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

-- ---------- token_consumer ----------
DROP TABLE IF EXISTS `token_consumer`;
CREATE TABLE `token_consumer` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `method` VARCHAR(255) DEFAULT NULL,
  `param` TEXT,
  `result` TEXT,
  `prompt_token_count` INT DEFAULT NULL,
  `candidates_token_count` INT DEFAULT NULL,
  `total_token_count` INT DEFAULT NULL,
  `cached_content_token_count` INT DEFAULT NULL,
  `cost_time` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
