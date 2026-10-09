-- 系统配置与后台菜单初始化（UTF-8，可重复执行）
-- 已有数据库执行本脚本；全新数据库在导入 sql/ymcloud.sql 后执行本脚本。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `system_setting` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setting_key` varchar(128) NOT NULL COMMENT '配置键',
  `setting_value` varchar(255) NOT NULL COMMENT '配置值',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';

START TRANSACTION;

-- 默认保持注册开启；重复执行不会覆盖管理员已经保存的配置。
INSERT INTO system_setting (setting_key, setting_value)
SELECT 'register_enabled', 'true'
WHERE NOT EXISTS (SELECT 1 FROM system_setting WHERE setting_key = 'register_enabled');

INSERT INTO auth (name, parent_id, path, route_name, component, icon, type, status)
SELECT '常用设置', 0, '/admin/common', 'AdminCommon', 'Layout', 'Setting', 'M', 1
WHERE NOT EXISTS (SELECT 1 FROM auth WHERE path = '/admin/common' AND type = 'M');

SET @common_menu_id = (SELECT id FROM auth WHERE path = '/admin/common' AND type = 'M' LIMIT 1);

INSERT INTO auth (code, name, parent_id, path, route_name, component, type, status)
SELECT 'system:setting:list', '系统配置', @common_menu_id, 'system-setting',
       'AdminSystemSetting', 'admin/common/SystemSetting', 'C', 1
WHERE NOT EXISTS (SELECT 1 FROM auth WHERE code = 'system:setting:list');

SET @setting_menu_id = (SELECT id FROM auth WHERE code = 'system:setting:list' LIMIT 1);

INSERT INTO auth (code, name, parent_id, type, status)
SELECT 'system:setting:edit', '修改系统配置', @setting_menu_id, 'F', 1
WHERE NOT EXISTS (SELECT 1 FROM auth WHERE code = 'system:setting:edit');

-- 仅授予管理员，不扩展普通用户或出题人的权限。
INSERT INTO role_auth (role_id, auth_id)
SELECT r.id, a.id
FROM role r
JOIN auth a ON a.id = @common_menu_id OR a.code IN ('system:setting:list', 'system:setting:edit')
WHERE r.code = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM role_auth ra WHERE ra.role_id = r.id AND ra.auth_id = a.id);

COMMIT;
