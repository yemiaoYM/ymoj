-- RBAC管理初始化（UTF-8，可重复执行）
-- 在20261009_system_setting.sql之后执行，不覆盖已分配的角色权限。
SET NAMES utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='role' AND column_name='built_in')=0,
  'ALTER TABLE role ADD COLUMN built_in tinyint(1) NOT NULL DEFAULT 0 COMMENT ''系统内置角色''', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='auth' AND column_name='built_in')=0,
  'ALTER TABLE auth ADD COLUMN built_in tinyint(1) NOT NULL DEFAULT 0 COMMENT ''系统内置权限''', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='role' AND index_name='uniq_role_code')=0,
  'ALTER TABLE role ADD UNIQUE KEY uniq_role_code(code)', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='auth' AND index_name='uniq_auth_code')=0,
  'ALTER TABLE auth ADD UNIQUE KEY uniq_auth_code(code)', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

START TRANSACTION;
SET @initialize_rbac = NOT EXISTS (SELECT 1 FROM system_setting WHERE setting_key='rbac_initialized');
UPDATE role SET built_in=1 WHERE code IN ('USER','ADMIN','PROBLEM_ADMIN');
UPDATE auth SET built_in=1 WHERE id IN
 (1,2,3,4,5,11,12,21,31,32,33,41,42,51,111,211,212,213,214,311,312,313,331,332,333,341,342,343,411,412,413,421,422,423,424,511,512,513,514,515,516,517);

SET @common_id = (SELECT id FROM auth WHERE path='/admin/common' AND type='M' LIMIT 1);
INSERT INTO auth (code,name,parent_id,type,status,built_in)
SELECT 'admin:access','进入管理后台',0,'F',1,1
WHERE NOT EXISTS(SELECT 1 FROM auth WHERE code='admin:access');

INSERT INTO auth (code,name,parent_id,path,route_name,component,type,status,built_in)
SELECT 'system:role:list','角色管理',@common_id,'role','AdminRoleList','admin/common/RoleList','C',1,1
WHERE NOT EXISTS(SELECT 1 FROM auth WHERE code='system:role:list');
SET @role_menu = (SELECT id FROM auth WHERE code='system:role:list' LIMIT 1);
INSERT INTO auth (code,name,parent_id,type,status,built_in)
SELECT p.code,p.name,@role_menu,'F',1,1 FROM (
 SELECT 'system:role:query' AS code,'查询角色' AS name UNION ALL
 SELECT 'system:role:add','新增角色' UNION ALL
 SELECT 'system:role:edit','修改角色' UNION ALL
 SELECT 'system:role:remove','删除角色' UNION ALL
 SELECT 'system:role:auth','分配角色权限'
) p WHERE NOT EXISTS(SELECT 1 FROM auth a WHERE a.code=p.code);

INSERT INTO auth (code,name,parent_id,path,route_name,component,type,status,built_in)
SELECT 'system:auth:list','菜单权限',@common_id,'auth','AdminAuthList','admin/common/AuthList','C',1,1
WHERE NOT EXISTS(SELECT 1 FROM auth WHERE code='system:auth:list');
SET @auth_menu = (SELECT id FROM auth WHERE code='system:auth:list' LIMIT 1);
INSERT INTO auth (code,name,parent_id,type,status,built_in)
SELECT p.code,p.name,@auth_menu,'F',1,1 FROM (
 SELECT 'system:auth:add' AS code,'新增菜单权限' AS name UNION ALL
 SELECT 'system:auth:edit','修改菜单权限' UNION ALL
 SELECT 'system:auth:remove','删除菜单权限'
) p WHERE NOT EXISTS(SELECT 1 FROM auth a WHERE a.code=p.code);
INSERT INTO auth (code,name,parent_id,type,status,built_in)
SELECT 'user:user:role','分配用户角色',(SELECT id FROM auth WHERE code='user:user:list' LIMIT 1),'F',1,1
WHERE NOT EXISTS(SELECT 1 FROM auth WHERE code='user:user:role');

-- 新功能授予管理员；保留其他既有授权，出题人继续可进入后台。
INSERT INTO role_auth (role_id,auth_id)
SELECT r.id,a.id FROM role r JOIN auth a
 ON (r.code='ADMIN' AND (a.code LIKE 'system:role:%' OR a.code LIKE 'system:auth:%' OR a.code='user:user:role'))
 OR (r.code IN ('ADMIN','PROBLEM_ADMIN') AND a.code='admin:access')
WHERE @initialize_rbac = 1
  AND NOT EXISTS(SELECT 1 FROM role_auth ra WHERE ra.role_id=r.id AND ra.auth_id=a.id);

-- 为既有页面授权补齐上级目录。
INSERT INTO role_auth (role_id,auth_id)
WITH RECURSIVE ancestors AS (
 SELECT ra.role_id,a.id,a.parent_id FROM role_auth ra JOIN auth a ON a.id=ra.auth_id
 UNION ALL
 SELECT c.role_id,a.id,a.parent_id FROM ancestors c JOIN auth a ON a.id=c.parent_id
)
SELECT DISTINCT c.role_id,c.id FROM ancestors c
WHERE @initialize_rbac = 1
  AND NOT EXISTS(SELECT 1 FROM role_auth ra WHERE ra.role_id=c.role_id AND ra.auth_id=c.id);

-- 为尚无角色的既有账户补齐普通用户角色，不触碰超级管理员。
INSERT INTO user_role (user_id,role_id)
SELECT u.id,r.id FROM user u JOIN role r ON r.code='USER'
WHERE @initialize_rbac = 1 AND u.id <> 1 AND NOT EXISTS(SELECT 1 FROM user_role ur WHERE ur.user_id=u.id);
-- 后续重复执行不恢复管理员已撤销的角色或权限。
INSERT INTO system_setting (setting_key,setting_value)
SELECT 'rbac_initialized','true' WHERE @initialize_rbac = 1;
COMMIT;
