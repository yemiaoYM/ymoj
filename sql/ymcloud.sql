/*
 Navicat Premium Dump SQL

 Source Server         : myy-ecs-mysql
 Source Server Type    : MySQL
 Source Server Version : 80039 (8.0.39)
 Source Host           : 47.93.124.26:3306
 Source Schema         : ymcloud

 Target Server Type    : MySQL
 Target Server Version : 80039 (8.0.39)
 File Encoding         : 65001

 Date: 25/09/2025 22:27:15
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for announcement
-- ----------------------------
DROP TABLE IF EXISTS `announcement`;
CREATE TABLE `announcement`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公告标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '公告内容（支持HTML/Markdown）',
  `type` tinyint NOT NULL DEFAULT 0 COMMENT '公告类型：0=系统公告，1=比赛公告，2=其他',
  `contest_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '所属比赛ID',
  `priority` int NOT NULL DEFAULT 0 COMMENT '公告权重（值越大越靠前）',
  `is_pinned` tinyint NOT NULL DEFAULT 0 COMMENT '是否置顶：1=置顶，0=不置顶',
  `is_visible` tinyint NOT NULL DEFAULT 1 COMMENT '是否可见：1=可见，0=隐藏',
  `publish_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间（支持定时发布）',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '过期时间（为空则长期有效）',
  `link_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '跳转链接（可选）',
  `author_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '发布人ID（user.id）',
  `author_username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发布人用户名',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '公告表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for auth
-- ----------------------------
DROP TABLE IF EXISTS `auth`;
CREATE TABLE `auth`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限编码（如 problem:add, user:delete）',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限名称（中文名，如 新增题目，删除用户）',
  `parent_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '父权限ID',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路由路径',
  `route_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路由名称',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '前端组件路径（菜单权限时使用）',
  `icon` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '前端菜单图标',
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '权限状态：0=禁用，1=启用',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 303 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for contest
-- ----------------------------
DROP TABLE IF EXISTS `contest`;
CREATE TABLE `contest`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '比赛ID',
  `creator_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '比赛创建者用户ID',
  `creator_username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '比赛创建者用户名或主办方',
  `image` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '比赛Logo图片路径',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '比赛标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '比赛说明内容',
  `source_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '来源比赛ID（0表示原创，其它为克隆赛ID）',
  `rated` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否计入Rating积分（0=否，1=是）',
  `mode` tinyint NOT NULL DEFAULT 0 COMMENT '赛制模式：0=ACM，1=OI，2=IOI',
  `style` tinyint NOT NULL DEFAULT 0 COMMENT '比赛类型：0=其他，1=周赛，2=月赛，3=娱乐赛，4=挑战赛',
  `visibility` tinyint NOT NULL DEFAULT 0 COMMENT '访问权限：0=公开，1=私有（进入需密码）',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '比赛密码（私有模式时使用）',
  `start_time` datetime NULL DEFAULT NULL COMMENT '比赛开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '比赛结束时间',
  `duration` bigint NULL DEFAULT NULL COMMENT '比赛时长（单位：分钟）',
  `allow_end_submit` tinyint(1) NULL DEFAULT 0 COMMENT '是否允许比赛结束后进行提交',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '比赛表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for contest_problem
-- ----------------------------
DROP TABLE IF EXISTS `contest_problem`;
CREATE TABLE `contest_problem`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `display_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '该题目在比赛中的顺序id',
  `contest_id` bigint UNSIGNED NOT NULL COMMENT '比赛id',
  `problem_id` bigint UNSIGNED NOT NULL COMMENT '题目id',
  `display_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '该题目在比赛中的标题，默认为原名字',
  `color` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '气球颜色',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_contest_display`(`contest_id` ASC, `display_id` ASC) USING BTREE,
  INDEX `idx_contest_problem`(`contest_id` ASC, `problem_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '比赛题目表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for contest_user
-- ----------------------------
DROP TABLE IF EXISTS `contest_user`;
CREATE TABLE `contest_user`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `contest_id` bigint UNSIGNED NOT NULL COMMENT '比赛ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '比赛报名表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for file
-- ----------------------------
DROP TABLE IF EXISTS `file`;
CREATE TABLE `file`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始文件名，例如 data.zip',
  `file_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件类型/MIME类型，例如 application/zip、image/png',
  `file_size` bigint UNSIGNED NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `file_ext` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件扩展名，例如 zip、png、pdf',
  `storage_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件存储相对路径，例如 upload/problem/1001/data.zip',
  `storage_type` tinyint NOT NULL DEFAULT 0 COMMENT '存储方式：0=本地，1=OSS，2=MinIO 等',
  `url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件可访问URL（如果是OSS/MinIO则存完整URL）',
  `uploader_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '上传者用户ID',
  `uploader_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '上传者用户名',
  `biz_type` tinyint NOT NULL DEFAULT 0 COMMENT '业务类型：0=通用，1=题目附件，2=题目数据包，3=比赛附件，4=公告附件，5=用户头像',
  `biz_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '业务ID，例如题目ID、比赛ID',
  `is_public` tinyint NOT NULL DEFAULT 1 COMMENT '是否公开访问：0=私有，1=公开',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文件信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for judge
-- ----------------------------
DROP TABLE IF EXISTS `judge`;
CREATE TABLE `judge`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评测ID',
  `problem_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '题目ID',
  `problem_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '题目标题',
  `display_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '题目展示ID，例如 ABC100-A',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户名',
  `language` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '编程语言名称',
  `code` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '用户提交代码',
  `code_length` int NULL DEFAULT NULL COMMENT '代码长度，单位：字符',
  `status` int NULL DEFAULT 0 COMMENT '评测状态',
  `score` int NULL DEFAULT NULL COMMENT '得分，ACM 模式为 NULL，OI 模式使用',
  `time` bigint NULL DEFAULT 0 COMMENT '执行耗时(ms)',
  `memory` bigint NULL DEFAULT 0 COMMENT '内存使用(KB)',
  `error_message` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '编译错误/运行时错误信息',
  `share` tinyint NOT NULL DEFAULT 0 COMMENT '是否公开代码：0=私有，1=公开',
  `is_manual` tinyint NOT NULL DEFAULT 0 COMMENT '是否为人工评测记录：0=自动，1=人工',
  `contest_id` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属比赛ID，非比赛默认为0',
  `contest_problem_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '比赛题目ID',
  `judge_server` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '执行判题的机器标识',
  `ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '提交用户IP',
  `version` int NULL DEFAULT 1 COMMENT '乐观锁版本号',
  `vjudge_submit_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT 'vjudge判题在其它oj的提交id',
  `vjudge_username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'vjudge判题在其它oj的提交用户名',
  `vjudge_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'vjudge判题在其它oj的提交账号密码',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_created`(`user_id` ASC, `created_time` ASC) USING BTREE,
  INDEX `idx_contest_created`(`contest_id` ASC, `created_time` ASC) USING BTREE,
  INDEX `idx_contest_display_status`(`contest_id` ASC, `display_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_user_problem_contest`(`user_id` ASC, `problem_id` ASC, `contest_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_problem_id`(`problem_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10121 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '判题记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for judge_case
-- ----------------------------
DROP TABLE IF EXISTS `judge_case`;
CREATE TABLE `judge_case`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评测样例记录ID',
  `judge_id` bigint UNSIGNED NOT NULL COMMENT '关联的判题记录ID（judge.id）',
  `case_index` int NOT NULL DEFAULT 1 COMMENT '样例在评测中的顺序编号（从1开始）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '评测结果',
  `time` bigint NULL DEFAULT NULL COMMENT '运行耗时(ms)',
  `memory` bigint NULL DEFAULT NULL COMMENT '运行内存(KB)',
  `score` int NULL DEFAULT NULL COMMENT '该样例得分（OI模式下有效）',
  `error_message` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '运行错误信息（CE、RE、WA等时可能会有）',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
  `output` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '用户程序输出（部分信息）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_judge_id`(`judge_id` ASC) USING BTREE,
  INDEX `idx_judge_status`(`judge_id` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 489 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '判题样例结果表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for language
-- ----------------------------
DROP TABLE IF EXISTS `language`;
CREATE TABLE `language`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '语言ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '语言名称，如 C++ 17',
  `image` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'docker语言镜像，如：gcc:10,openjdk:17-slim',
  `env` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'docker自定义镜像环境,如oj-cpp-env',
  `version` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '语言版本信息，如 g++ 9.4 / Python 3.10',
  `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'MIME类型，用于前端代码高亮，如 text/x-c++src',
  `file_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '默认文件名，如main.py,Main.java,main.cpp,main.c',
  `file_extension` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '默认文件扩展名，如 .cpp、.py',
  `compile_cmd` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '编译指令，编译型语言需要，如 g++ main.cpp -o main',
  `run_cmd` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '运行指令，如 ./main、python3 main.py',
  `template_code` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '语言默认代码模板，显示在编辑器中',
  `code_template` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '题面中用于填空题的嵌套模板',
  `type` tinyint NOT NULL DEFAULT 0 COMMENT '语言类型：0=编译型，1=解释型',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态：1=启用，0=停用',
  `oj` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'myy' COMMENT '归属OJ标识，自身平台填myy',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '编程语言表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for problem
-- ----------------------------
DROP TABLE IF EXISTS `problem`;
CREATE TABLE `problem`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `problem_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '问题的自定义ID，可作为唯一标识，例如YMOJ-1000（本题库），P1005（洛谷）等等',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '题目标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '题目描述内容（题干）',
  `is_remote` tinyint(1) NULL DEFAULT 0 COMMENT '是否为vj判题',
  `input` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '输入描述',
  `output` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '输出描述',
  `examples` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '题面样例',
  `source` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '题目来源',
  `difficulty` tinyint NOT NULL DEFAULT 0 COMMENT '难度：0=未评定，1=入门，2=普及，3=提高，4=省选及以上',
  `mode` tinyint NOT NULL DEFAULT 0 COMMENT '评测模式：0=ACM，1=OI',
  `visibility` tinyint NOT NULL DEFAULT 1 COMMENT '可见性：1=公开，2=私有，3=比赛专用',
  `open_case_result` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否允许查看测试样例结果（0=否，1=是）',
  `time_limit` bigint NOT NULL DEFAULT 1000 COMMENT '时间限制（ms）',
  `memory_limit` bigint NOT NULL DEFAULT 65536 COMMENT '内存限制（KB）',
  `author_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '出题人ID（user.id）',
  `author_username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '出题人用户名',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `problem_id`(`problem_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '题目表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for problem_tag
-- ----------------------------
DROP TABLE IF EXISTS `problem_tag`;
CREATE TABLE `problem_tag`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `problem_id` bigint UNSIGNED NOT NULL COMMENT '题目ID',
  `tag_id` bigint UNSIGNED NOT NULL COMMENT '标签ID',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_problem_tag`(`problem_id` ASC, `tag_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '题目-标签关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for role
-- ----------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色编码（如 ADMIN, USER）',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称（中文名，如 管理员，普通用户）',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '角色描述',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '角色状态：0=禁用，1=启用',
  `created_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for role_auth
-- ----------------------------
DROP TABLE IF EXISTS `role_auth`;
CREATE TABLE `role_auth`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `auth_id` bigint UNSIGNED NOT NULL COMMENT '权限ID',
  `created_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_role_auth`(`role_id` ASC, `auth_id` ASC) USING BTREE,
  INDEX `idx_role_id`(`role_id` ASC) USING BTREE,
  INDEX `idx_auth_id`(`auth_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 79 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色权限关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tag
-- ----------------------------
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签名称',
  `parent_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '父标签ID',
  `color` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签颜色（如 #ff6600）',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 31 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tag_classification
-- ----------------------------
DROP TABLE IF EXISTS `tag_classification`;
CREATE TABLE `tag_classification`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签分类名称',
  `priority` int UNSIGNED NOT NULL DEFAULT 1 COMMENT '分类优先级（数值越小优先级越高）',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '标签分类表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录用户名（唯一）',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '昵称',
  `sex` tinyint NULL DEFAULT 0 COMMENT '性别：0=保密，1=男，2=女',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '邮箱',
  `avatar` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像 URL',
  `school` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '学校',
  `signature` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '个性签名',
  `blog` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '博客链接',
  `login_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最后登录 IP',
  `login_date` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '账号状态：1=正常，0=禁用',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `email`(`email` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_acproblem
-- ----------------------------
DROP TABLE IF EXISTS `user_acproblem`;
CREATE TABLE `user_acproblem`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `submission_id` bigint UNSIGNED NOT NULL COMMENT '提交ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名',
  `problem_id` bigint UNSIGNED NOT NULL COMMENT '题目ID',
  `display_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '题目展示ID，例如 YMOJ-1001',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_problem`(`user_id` ASC, `problem_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户已AC题目记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_role
-- ----------------------------
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `created_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_user_role`(`user_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_role_id`(`role_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户角色关联表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
