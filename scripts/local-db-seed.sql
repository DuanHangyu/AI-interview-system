-- ============================================================================
-- Minimal seed data for local development DB.
-- Passwords are PLAINTEXT (matches LoginService plaintext comparison).
-- Roles: STUDENT=0, TEACHER=1, ADMIN=2.
-- Apply on the `defense_test` database after local-db-schema.sql.
-- ============================================================================

SET NAMES utf8mb4;

-- ---------- 3 role accounts ----------
-- admin / admin123
INSERT INTO `user` (`name`, `account`, `password`, `phone`, `type`, `school_class`, `create_time`, `update_time`)
VALUES ('管理员', 'admin', 'admin123', '13800000000', 2, NULL, NOW(), NOW());

-- teacher / teacher123
INSERT INTO `user` (`name`, `account`, `password`, `phone`, `type`, `school_class`, `create_time`, `update_time`)
VALUES ('张老师', 'teacher', 'teacher123', '13800000001', 1, NULL, NOW(), NOW());

-- student / student123
INSERT INTO `user` (`name`, `account`, `password`, `phone`, `type`, `school_class`, `create_time`, `update_time`)
VALUES ('李同学', 'student', 'student123', '13800000002', 0, '计算机2101', NOW(), NOW());

-- ---------- project types (dashboards reference these) ----------
INSERT INTO `project_type` (`project_type_key`, `project_type`) VALUES
  ('course_project', '课程项目'),
  ('thesis_defense', '毕业答辩'),
  ('capability_test', '能力测评');
