package com.ymcloud.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从正式初始化脚本中提取存储测试所需的建表语句，种子数据和其他语句绝不执行。 */
final class InitializationSqlTestSupport {

    private static final Set<String> TABLES = Set.of("file", "problem", "judge", "test_data_version");
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)^CREATE\\s+TABLE\\s+`([a-z0-9_]+)`\\s*\\(");
    private static final Pattern REFERENCE = Pattern.compile("(?i)\\bREFERENCES\\s+`([a-z0-9_]+)`");
    private static final Pattern CONSTRAINT = Pattern.compile("(?i)\\bCONSTRAINT\\s+`([a-z0-9_]+)`");

    private InitializationSqlTestSupport() {
    }

    static String storageSchema(String prefix) throws IOException {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (directory != null) {
            Path script = directory.resolve("sql/ymcloud.sql");
            if (Files.isRegularFile(script)) {
                return storageSchema(Files.readString(script, StandardCharsets.UTF_8), prefix);
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("未找到 sql/ymcloud.sql，禁止使用旧表结构替代初始化脚本");
    }

    static String storageSchema(String source, String prefix) {
        if (!prefix.isEmpty() && !prefix.matches("ymcloud_storage_it_[0-9a-f]{16}_")) {
            throw new IllegalArgumentException("测试表前缀未通过安全校验");
        }
        Map<String, String> definitions = new LinkedHashMap<>();
        for (String statement : statements(source)) {
            Matcher table = CREATE_TABLE.matcher(statement);
            if (!table.find() || !TABLES.contains(table.group(1))) {
                continue;
            }
            String name = table.group(1);
            if (definitions.containsKey(name)) {
                throw new IllegalStateException("初始化脚本包含重复测试表：" + name);
            }
            Matcher reference = REFERENCE.matcher(statement);
            while (reference.find()) {
                if (!definitions.containsKey(reference.group(1))) {
                    throw new IllegalStateException("测试外键必须引用已创建的隔离表：" + reference.group(1));
                }
            }
            String isolated = statement;
            for (String target : TABLES) {
                isolated = isolated.replace("`" + target + "`", "`" + prefix + target + "`");
            }
            if (!prefix.isEmpty()) {
                Matcher constraint = CONSTRAINT.matcher(isolated);
                isolated = constraint.replaceAll(match -> "CONSTRAINT `" + prefix + match.group(1) + "`");
            }
            definitions.put(name, isolated);
        }
        if (!definitions.keySet().equals(TABLES)) {
            throw new IllegalStateException("初始化脚本必须包含 file、problem、judge、test_data_version 四张测试表");
        }
        return String.join(";\n", definitions.values()) + ";\n";
    }

    private static List<String> statements(String source) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : 0;
            if (quote != 0) {
                current.append(character);
                if (character == '\\' && next != 0) {
                    current.append(next);
                    index++;
                } else if (character == quote) {
                    if (next == quote) {
                        current.append(next);
                        index++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (character == '\'' || character == '"' || character == '`') {
                quote = character;
                current.append(character);
            } else if (character == '#' || (character == '-' && next == '-'
                    && (index + 2 == source.length() || Character.isWhitespace(source.charAt(index + 2))))) {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
                current.append('\n');
            } else if (character == '/' && next == '*') {
                int end = source.indexOf("*/", index + 2);
                if (end < 0) {
                    throw new IllegalStateException("初始化 SQL 注释未闭合");
                }
                index = end + 1;
                current.append(' ');
            } else if (character == ';') {
                if (!current.toString().isBlank()) {
                    statements.add(current.toString().strip());
                }
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (quote != 0 || !current.toString().isBlank()) {
            throw new IllegalStateException("初始化 SQL 存在未结束的语句");
        }
        return statements;
    }
}
