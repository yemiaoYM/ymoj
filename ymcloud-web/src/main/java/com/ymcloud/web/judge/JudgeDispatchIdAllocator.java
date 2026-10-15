package com.ymcloud.web.judge;

import com.ymcloud.common.exception.judge.JudgeException;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeDispatch;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** 正式提交和自测使用数据库统一序号，保持现有前端 number 的精确范围。 */
@Component
public class JudgeDispatchIdAllocator {

    public static final long MAX_SAFE_ID = 9_007_199_254_740_991L;

    private static final String COUNTER_SQL = "SELECT AUTO_INCREMENT FROM information_schema.tables "
            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='judge_dispatch'";

    private final JudgeDispatchEntityService dispatches;
    private final JudgeEntityService judges;
    private final JdbcTemplate jdbc;

    public JudgeDispatchIdAllocator(JudgeDispatchEntityService dispatches,
                                    JudgeEntityService judges,
                                    JdbcTemplate jdbc) {
        this.dispatches = dispatches;
        this.judges = judges;
        this.jdbc = jdbc;
    }

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

    /** MySQL 默认缓存统计 24 小时；在同一连接读取实时序号并恢复该连接的原设置。 */
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
                    statement.execute("SET SESSION information_schema_stats_expiry=" + original);
                }
            }
        });
    }

    /** AUTO_INCREMENT 统计不能代替列定义校验，移除自增后表统计仍可能保留旧序号。 */
    private boolean hasAutoIncrementColumn(Statement statement) throws SQLException {
        try (ResultSet column = statement.executeQuery("SELECT EXTRA FROM information_schema.columns "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='judge_dispatch' AND COLUMN_NAME='id'")) {
            return column.next() && column.getString(1) != null
                    && column.getString(1).contains("auto_increment");
        }
    }

    /** 列定义已校验自增；首次插入前 MySQL 的空表计数可为 NULL，仅真实空表视为下一号 1。 */
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

    /** 仅在提交事务中保留一个未完成快照的行，回滚后不会留下可发布任务。 */
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
