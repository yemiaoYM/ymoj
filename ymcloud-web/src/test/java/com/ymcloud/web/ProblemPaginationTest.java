package com.ymcloud.web;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.pojo.entity.Problem;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 题目保留字字段与分页计数 SQL 的回归验证。
 */
class ProblemPaginationTest {

    /**
     * 重现原始语句解析异常，确认实体生成的查询可以正常优化计数并保留过滤条件。
     * @throws JSQLParserException 修复后的 SQL 仍不能解析时失败
     */
    @Test
    void escapedStatementProducesOptimizedCount() throws JSQLParserException {
        String original = "SELECT id,problem_id,title,content,time_limit,memory_limit,input,output,examples,source,"
                + "difficulty,judge_mode,visibility,open_case_result,test_data_version_id,author_id,author_username,"
                + "created_time,updated_time FROM problem ORDER BY created_time DESC";
        assertThrows(JSQLParserException.class, () -> CCJSqlParserUtil.parse(original));
        var table = TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), Problem.class);
        String select = "SELECT " + table.getAllSqlSelect() + " FROM problem";
        CCJSqlParserUtil.parse(select + " ORDER BY created_time DESC");
        var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        assertEquals("SELECT COUNT(*) AS total FROM problem",
                pagination.autoCountSql(new Page<>(), select + " ORDER BY created_time DESC"));
        assertEquals("SELECT COUNT(*) AS total FROM problem WHERE visibility = ? AND title LIKE ?",
                pagination.autoCountSql(new Page<>(), select
                        + " WHERE visibility = ? AND title LIKE ? ORDER BY created_time DESC"));
    }
}
