package com.ymcloud.web.judge;

import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeDispatch;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-12
 * @Description: 正式提交和自测使用数据库统一序号，保持现有前端 number 的精确范围。
 */
@Component
public class JudgeDispatchIdAllocator {

    public static final long MAX_SAFE_ID = 9_007_199_254_740_991L;

    private static final String COUNTER_SQL = "SELECT AUTO_INCREMENT FROM information_schema.tables "
            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='judge_dispatch'";

    @Autowired
    private JudgeDispatchEntityService dispatches;

    @Autowired
    private JudgeEntityService judges;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 启动时核对自增定义和前端可精确表示的序号范围，不在运行时修改表结构。
     * @throws IllegalStateException 自增序号未超过历史提交或超出安全整数范围
     */
    @PostConstruct
    public void verifyCounter() {
        Judge latest = judges.lambdaQuery()
                .select(Judge::getId)
                .orderByDesc(Judge::getId)
                .last("LIMIT 1")
                .one();
        long existing = latest == null ? 0 : latest.getId();
        Long next = freshCounter();
        if (next == null || next <= existing || next > MAX_SAFE_ID) {
            throw new IllegalStateException("判题任务序号未升级或超出前端安全范围，请先执行独立 Worker 数据库升级脚本");
        }
    }

    /**
     * 绕过 MySQL 元数据统计缓存读取下一自增值，并恢复当前连接的原设置。
     * 固定系统表查询不接受外部 SQL 片段；lambdaQuery 无法替代会话级元数据操作。
     * 此值只用于启动校验，实际 ID 由 reserve 中的数据库 INSERT 分配，禁止 MAX(id) + 1。
     * @return 下一自增值；定义或统计不可用时返回 null
     */
    private Long freshCounter() {
        return jdbc.execute((ConnectionCallback<Long>) connection -> {
            try (Statement statement = connection.createStatement()) {
                long original;
                try (ResultSet setting = statement.executeQuery("SELECT @@session.information_schema_stats_expiry")) {
                    if (!setting.next()) {
                        throw new IllegalStateException("无法读取数据库统计配置");
                    }
                    original = setting.getLong(1);
                }
                statement.execute("SET SESSION information_schema_stats_expiry=0");
                try {
                    if (!hasAutoIncrementColumn(statement)) {
                        return null;
                    }
                    // InnoDB 冷启动时未打开的表可能返回旧计数；先实际读取表，再查询实时自增值。
                    try (ResultSet row = statement.executeQuery("SELECT id FROM judge_dispatch LIMIT 1")) {
                        row.next();
                    }
                    Long next = null;
                    try (ResultSet counter = statement.executeQuery(COUNTER_SQL)) {
                        if (!counter.next()) {
                            return null;
                        }
                        long value = counter.getLong(1);
                        if (!counter.wasNull()) {
                            next = value;
                        }
                    }
                    return next == null ? initialCounter(statement) : next;
                } finally {
                    try (PreparedStatement restore = connection.prepareStatement(
                            "SET SESSION information_schema_stats_expiry=?")) {
                        restore.setLong(1, original);
                        restore.execute();
                    }
                }
            }
        });
    }

    /**
     * 校验列定义，避免移除自增后的残留统计被误认为有效序号。
     * @param statement 当前元数据连接的语句
     * @return id 列是否声明自增
     * @throws SQLException 元数据读取失败
     */
    private boolean hasAutoIncrementColumn(Statement statement) throws SQLException {
        try (ResultSet column = statement.executeQuery("SELECT EXTRA FROM information_schema.columns "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='judge_dispatch' AND COLUMN_NAME='id'")) {
            return column.next() && column.getString(1) != null
                    && column.getString(1).contains("auto_increment");
        }
    }

    /**
     * 自增列首次插入前统计可为 null，仅真实空表按初始序号 1 校验。
     * @param statement 当前元数据连接的语句
     * @return 空表初始序号或重新读取的计数；无法确认时为 null
     * @throws SQLException 表或统计读取失败
     */
    private Long initialCounter(Statement statement) throws SQLException {
        try (ResultSet row = statement.executeQuery("SELECT id FROM judge_dispatch LIMIT 1")) {
            if (!row.next()) {
                return 1L;
            }
        }
        // 另一 Web 实例可能刚完成首次插入，重新读取已经初始化的计数，避免并发启动误拒绝。
        try (ResultSet counter = statement.executeQuery(COUNTER_SQL)) {
            if (!counter.next()) {
                return null;
            }
            long next = counter.getLong(1);
            return counter.wasNull() ? null : next;
        }
    }

    /**
     * 在调用方提交事务内插入任务行，由数据库自增分配正式提交或自测 ID。
     * 回滚不会留下可发布任务，允许自增序号出现空洞。
     * @param dispatch 待持久化的任务快照，插入后回填 ID
     * @return 前端安全整数范围内的提交 ID
     * @throws JudgeException 插入失败或数据库返回的 ID 越界
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Long reserve(JudgeDispatch dispatch) {
        if (!dispatches.save(dispatch)) {
            throw new JudgeException("任务创建失败，请稍后再试");
        }
        Long id = dispatch.getId();
        if (id == null || id <= 0 || id > MAX_SAFE_ID) {
            throw new JudgeException("判题提交序号超出允许范围，请联系管理员");
        }
        return id;
    }
}
