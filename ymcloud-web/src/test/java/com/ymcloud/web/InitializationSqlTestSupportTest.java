package com.ymcloud.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 验证测试建表脚本不导入真实账户、不执行无关 SQL 且保留正式表约束
 */
class InitializationSqlTestSupportTest {

    private static final String DEFINITIONS = """
            CREATE TABLE `file` (`id` bigint, `name` text COMMENT '引号''与分号;保持原样');
            CREATE TABLE `problem` (`id` bigint);
            CREATE TABLE `test_data_version` (`id` bigint PRIMARY KEY);
            CREATE TABLE `judge` (`id` bigint, `test_data_version_id` bigint,
                CONSTRAINT `fk_judge_test_data_version` FOREIGN KEY (`test_data_version_id`)
                REFERENCES `test_data_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT);
            """;

    /**
     * 排除种子数据和任意业务写入，保留完整正式表定义。
     */
    @Test
    void excludesSeedDataAndPreservesForeignKeysAndConstraintNames() {
        String schema = InitializationSqlTestSupport.storageSchema("""
                -- CREATE TABLE `file` (`fake` bigint);
                SET NAMES utf8mb4;
                CREATE TABLE `user` (`id` bigint);
                """ + DEFINITIONS + """
                INSERT INTO `user` VALUES (1);
                DELETE FROM `file`;
                ALTER TABLE `problem` ADD `fake` bigint;
                DROP TABLE `judge`;
                """);
        assertEquals(4, schema.lines().filter(line -> line.startsWith("CREATE TABLE")).count());
        assertFalse(schema.contains("INSERT"));
        assertFalse(schema.contains("DELETE FROM"));
        assertFalse(schema.contains("ALTER TABLE"));
        assertFalse(schema.contains("DROP TABLE"));
        assertFalse(schema.contains("`user`"));
        assertTrue(schema.contains("REFERENCES `test_data_version`"));
        assertTrue(schema.contains("CONSTRAINT `fk_judge_test_data_version`"));
        assertTrue(schema.contains("COMMENT '引号''与分号;保持原样'"));
    }

    /**
     * 表缺失、重复或外键跨出测试表集合时，在执行前失败。
     */
    @Test
    void rejectsMissingDuplicateAndExternalReferencedTables() {
        assertThrows(IllegalStateException.class, () -> InitializationSqlTestSupport.storageSchema(
                "CREATE TABLE `file` (`id` bigint);"));
        assertThrows(IllegalStateException.class, () -> InitializationSqlTestSupport.storageSchema(
                DEFINITIONS + "CREATE TABLE `file` (`id` bigint);"));
        assertThrows(IllegalStateException.class, () -> InitializationSqlTestSupport.storageSchema(
                DEFINITIONS.replace("REFERENCES `test_data_version`", "REFERENCES `user`")));
    }

    /**
     * 未闭合的字符串与注释不得被截断为可执行语句。
     */
    @Test
    void rejectsIncompleteStatementsBeforeExecution() {
        assertThrows(IllegalStateException.class, () -> InitializationSqlTestSupport.storageSchema(
                DEFINITIONS + "INSERT INTO `user` VALUES ('unterminated"));
        assertThrows(IllegalStateException.class, () -> InitializationSqlTestSupport.storageSchema(
                DEFINITIONS + "/* unclosed"));
    }

    /**
     * 验证真实初始化脚本可提取当前存储结构且不包含种子数据。
     *
     * @throws Exception 初始化脚本读取失败
     */
    @Test
    void extractsCurrentBaselineWithoutInitializationData() throws Exception {
        String schema = InitializationSqlTestSupport.storageSchema();
        assertEquals(4, schema.lines().filter(line -> line.startsWith("CREATE TABLE")).count());
        assertTrue(schema.contains("`storage_profile`"));
        assertTrue(schema.contains("`manifest_sha256`"));
        assertTrue(schema.contains("REFERENCES `test_data_version`"));
        assertFalse(schema.contains("INSERT"));
    }
}
