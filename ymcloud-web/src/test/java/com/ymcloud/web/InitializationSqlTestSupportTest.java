package com.ymcloud.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 核对数据库集成测试只创建自己的表，不导入真实初始化账户或执行业务写入。 */
class InitializationSqlTestSupportTest {

    private static final String PREFIX = "ymcloud_storage_it_0123456789abcdef_";
    private static final String DEFINITIONS = """
            CREATE TABLE `file` (`id` bigint, `name` text COMMENT '引号''与分号;保持原样');
            CREATE TABLE `problem` (`id` bigint);
            CREATE TABLE `test_data_version` (`id` bigint PRIMARY KEY);
            CREATE TABLE `judge` (`id` bigint, `test_data_version_id` bigint,
                CONSTRAINT `fk_judge_test_data_version` FOREIGN KEY (`test_data_version_id`)
                REFERENCES `test_data_version` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT);
            """;

    @Test
    void excludesSeedDataAndRewritesForeignKeysAndConstraintNames() {
        String schema = InitializationSqlTestSupport.storageSchema("""
                -- CREATE TABLE `file` (`fake` bigint);
                SET NAMES utf8mb4;
                CREATE TABLE `user` (`id` bigint);
                """ + DEFINITIONS + """
                INSERT INTO `user` VALUES (1);
                DELETE FROM `file`;
                ALTER TABLE `problem` ADD `fake` bigint;
                DROP TABLE `judge`;
                """, PREFIX);
        assertEquals(4, schema.lines().filter(line -> line.startsWith("CREATE TABLE")).count());
        assertFalse(schema.contains("INSERT"));
        assertFalse(schema.contains("DELETE FROM"));
        assertFalse(schema.contains("ALTER TABLE"));
        assertFalse(schema.contains("DROP TABLE"));
        assertFalse(schema.contains("`user`"));
        assertTrue(schema.contains("REFERENCES `" + PREFIX + "test_data_version`"));
        assertTrue(schema.contains("CONSTRAINT `" + PREFIX + "fk_judge_test_data_version`"));
        assertTrue(schema.contains("COMMENT '引号''与分号;保持原样'"));
    }

    @Test
    void rejectsMissingDuplicateAndExternalReferencedTables() {
        assertThrows(IllegalStateException.class, () -> InitializationSqlTestSupport.storageSchema(
                "CREATE TABLE `file` (`id` bigint);", ""));
        assertThrows(IllegalStateException.class, () -> InitializationSqlTestSupport.storageSchema(
                DEFINITIONS + "CREATE TABLE `file` (`id` bigint);", ""));
        assertThrows(IllegalStateException.class, () -> InitializationSqlTestSupport.storageSchema(
                DEFINITIONS.replace("REFERENCES `test_data_version`", "REFERENCES `user`"), PREFIX));
    }

    @Test
    void rejectsUnsafePrefixAndIncompleteStatementsBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> InitializationSqlTestSupport.storageSchema(
                DEFINITIONS, "production_"));
        assertThrows(IllegalStateException.class, () -> InitializationSqlTestSupport.storageSchema(
                DEFINITIONS + "INSERT INTO `user` VALUES ('unterminated", ""));
        assertThrows(IllegalStateException.class, () -> InitializationSqlTestSupport.storageSchema(
                DEFINITIONS + "/* unclosed", ""));
    }

    @Test
    void extractsCurrentBaselineWithoutInitializationData() throws Exception {
        String schema = InitializationSqlTestSupport.storageSchema("");
        assertEquals(4, schema.lines().filter(line -> line.startsWith("CREATE TABLE")).count());
        assertTrue(schema.contains("`storage_profile`"));
        assertTrue(schema.contains("`manifest_sha256`"));
        assertTrue(schema.contains("REFERENCES `test_data_version`"));
        assertFalse(schema.contains("INSERT"));
    }
}
