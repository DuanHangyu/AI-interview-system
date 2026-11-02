-- V3：教师按维度复核评分（报告中与 AI 评分对照展示）
ALTER TABLE `student_assessment_record`
  ADD COLUMN `teacher_review` TEXT NULL COMMENT '教师复核结果 JSON' AFTER `re_analysis`;
