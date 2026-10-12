-- 第一阶段（v3.2.0）已有数据库升级至独立 Worker；全新安装仅使用 ymcloud.sql。
-- 在维护窗口停止新提交并排空旧任务后执行。保留全部历史提交和文件，不重建数据库。
-- 仅执行一次；DDL 非事务，不使用 --force，先确认 judge 尚无 execution_id、judge_dispatch 尚不存在。
SET NAMES utf8mb4;

ALTER TABLE `judge`
  ADD COLUMN `execution_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
    COMMENT '当前评测执行标识；历史记录为空' AFTER `test_data_version_id`;

CREATE TABLE `judge_dispatch` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '持久化任务ID',
  `submission_id` bigint unsigned DEFAULT NULL COMMENT '与任务ID一致；仅同事务预分配时暂为空',
  `execution_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行标识，防止旧结果覆盖',
  `user_id` bigint unsigned NOT NULL COMMENT '任务所属用户',
  `strategy` int NOT NULL COMMENT '0正式提交；1自测',
  `problem_id` bigint unsigned DEFAULT NULL,
  `test_data_version_id` bigint DEFAULT NULL COMMENT '创建任务时固定的测试版本',
  `task_payload` longtext NOT NULL COMMENT '不可变消息快照，包含私有代码及自测输入',
  `result_payload` longtext DEFAULT NULL COMMENT '最近接收的有效结果',
  `dispatch_state` int NOT NULL DEFAULT 0 COMMENT '0待发布；1待结果；2已完成',
  `status` int NOT NULL DEFAULT 5,
  `last_result_sequence` int NOT NULL DEFAULT 0,
  `publish_attempts` int NOT NULL DEFAULT 0,
  `recovery_attempts` int NOT NULL DEFAULT 0,
  `next_attempt_time` datetime NOT NULL COMMENT '下次发布或恢复时间（UTC）',
  `published_time` datetime DEFAULT NULL COMMENT '最近发布时间（UTC）',
  `deadline_time` datetime NOT NULL COMMENT '任务总截止时间（UTC）',
  `lease_token` varchar(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL COMMENT '发布租约截止时间（UTC）',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_dispatch_submission` (`submission_id`),
  UNIQUE KEY `uniq_dispatch_execution` (`execution_id`),
  KEY `idx_dispatch_pending` (`dispatch_state`,`next_attempt_time`),
  KEY `idx_dispatch_deadline` (`dispatch_state`,`deadline_time`),
  KEY `idx_dispatch_version` (`test_data_version_id`),
  CONSTRAINT `fk_dispatch_test_data_version` FOREIGN KEY (`test_data_version_id`)
    REFERENCES `test_data_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可靠任务发布与结果幂等状态';

-- 正式提交与自测共用任务箱自增 ID，延续已有提交编号，避免碰撞和 JavaScript 数字精度损失。
SET @worker_submission_start = (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `judge`);
SET @worker_allocate_ddl = CONCAT('ALTER TABLE `judge_dispatch` AUTO_INCREMENT = ', @worker_submission_start);
PREPARE worker_allocate_statement FROM @worker_allocate_ddl;
EXECUTE worker_allocate_statement;
DEALLOCATE PREPARE worker_allocate_statement;
