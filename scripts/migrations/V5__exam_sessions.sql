-- V5：考试场次（老师安排学生到场次，只能在场次时间内开始面试）+ 考场监控
SET NAMES utf8mb4;

-- 1. 场次增加结束时间（结束后不能再开始面试；已开始的可以答完）
ALTER TABLE `assessment_appointment_setting`
  ADD COLUMN `end_time` DATETIME NULL COMMENT '场次结束时间' AFTER `time_period`;
UPDATE `assessment_appointment_setting` SET `end_time` = DATE_ADD(`time_period`, INTERVAL 1 HOUR) WHERE `end_time` IS NULL;

-- 2. 学生所在场次
ALTER TABLE `student_assessment_appointment`
  ADD COLUMN `setting_id` INT NULL COMMENT '所在场次 id' AFTER `assessment_id`,
  ADD KEY `idx_saa_assessment_student` (`assessment_id`, `student_id`);
UPDATE `student_assessment_appointment` a
  JOIN `assessment_appointment_setting` s ON s.`assessment_id` = a.`assessment_id` AND s.`time_period` = a.`time_period`
  SET a.`setting_id` = s.`id`
  WHERE a.`setting_id` IS NULL;

-- 3. 取消缺考惩罚：缺考改为按场次时间实时计算，老师可重新安排场次
UPDATE `student_assessment_appointment` SET `state` = 0, `punish_state` = -1 WHERE `state` IN (3, 4);
UPDATE `assessment` SET `assessment_fail_punish` = 0;

-- 4. 答题记录的最后更新时间，考场监控中显示学生最后一次作答的时间
ALTER TABLE `student_assessment_question_answer`
  ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `create_time`;
