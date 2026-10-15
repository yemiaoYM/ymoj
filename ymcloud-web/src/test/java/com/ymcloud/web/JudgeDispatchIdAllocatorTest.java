package com.ymcloud.web;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.web.judge.JudgeDispatchIdAllocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 模拟 InnoDB 冷表统计，验证启动检查真实打开任务表后才读取自增序号。 */
class JudgeDispatchIdAllocatorTest {

    private static final String COUNTER_SQL = "SELECT AUTO_INCREMENT FROM information_schema.tables "
            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='judge_dispatch'";
    private static final String OPEN_TABLE_SQL = "SELECT id FROM judge_dispatch LIMIT 1";
    private static final long ORIGINAL_STATS_EXPIRY = 1234L;

    private Connection connection;
    private Statement statement;
    private Judge latest;
    private JudgeDispatchIdAllocator allocator;
    private List<String> statements;
    private boolean columnExists = true;
    private boolean autoIncrement = true;
    private boolean hasRows = true;
    private boolean tableOpened;
    private boolean counterExists = true;
    private Long coldCounter = 7L;
    private Long openedCounter = 8L;
    private String failingSql;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        statements = new ArrayList<>();
        when(statement.executeQuery(anyString())).thenAnswer(invocation -> query(invocation.getArgument(0)));
        when(statement.execute(anyString())).thenAnswer(invocation -> {
            statements.add(invocation.getArgument(0));
            return false;
        });

        JudgeEntityService judges = mock(JudgeEntityService.class);
        LambdaQueryChainWrapper<Judge> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(judges.lambdaQuery()).thenReturn(query);
        when(query.orderByDesc(any(SFunction.class))).thenReturn(query);
        when(query.last(anyString())).thenReturn(query);
        latest = new Judge();
        latest.setId(7L);
        when(query.one()).thenReturn(latest);
        allocator = new JudgeDispatchIdAllocator(mock(JudgeDispatchEntityService.class), judges,
                new JdbcTemplate(dataSource));
    }

    @Test
    void coldNonemptyTableIsOpenedBeforeReadingItsNextId() throws SQLException {
        // 真实故障：重启后统计返回最后已用序号 7，打开表后才返回下一号 8。
        assertDoesNotThrow(allocator::verifyCounter);
        assertTrue(statements.indexOf(OPEN_TABLE_SQL) < statements.indexOf(COUNTER_SQL));
        verifyRestoredAndClosed();
    }

    @Test
    void coldCounterThatLooksValidStillCannotBypassOpeningTheTable() throws SQLException {
        latest.setId(5L);
        assertDoesNotThrow(allocator::verifyCounter);
        assertTrue(tableOpened);
        assertTrue(statements.indexOf(OPEN_TABLE_SQL) < statements.indexOf(COUNTER_SQL));
        verifyRestoredAndClosed();
    }

    @Test
    void explicitlySeededCounterIsReadWithoutDerivingItFromExistingRows() throws SQLException {
        latest.setId(1000L);
        openedCounter = 1001L;
        assertDoesNotThrow(allocator::verifyCounter);
        verifyRestoredAndClosed();
    }

    @Test
    void insufficientLegacySeedStillFailsAfterOpeningTheTable() throws SQLException {
        latest.setId(1000L);
        assertThrows(IllegalStateException.class, allocator::verifyCounter);
        verifyRestoredAndClosed();
    }

    @Test
    void maximumSafeCounterIsAccepted() throws SQLException {
        latest.setId(JudgeDispatchIdAllocator.MAX_SAFE_ID - 1);
        openedCounter = JudgeDispatchIdAllocator.MAX_SAFE_ID;
        assertDoesNotThrow(allocator::verifyCounter);
        verifyRestoredAndClosed();
    }

    @Test
    void unsafeRealCounterIsRejectedEvenWhenColdStatisticsLookSafe() throws SQLException {
        latest.setId(5L);
        openedCounter = JudgeDispatchIdAllocator.MAX_SAFE_ID + 1;
        assertThrows(IllegalStateException.class, allocator::verifyCounter);
        verifyRestoredAndClosed();
    }

    @Test
    void missingAutoIncrementIsRejectedWithoutOpeningTheTable() throws SQLException {
        autoIncrement = false;
        assertThrows(IllegalStateException.class, allocator::verifyCounter);
        assertFalse(tableOpened);
        assertFalse(statements.contains(COUNTER_SQL));
        verifyRestoredAndClosed();
    }

    @Test
    void missingIdColumnCannotUseTheEmptyTableFallback() throws SQLException {
        columnExists = false;
        hasRows = false;
        latest.setId(0L);
        assertThrows(IllegalStateException.class, allocator::verifyCounter);
        assertFalse(tableOpened);
        verifyRestoredAndClosed();
    }

    @Test
    void emptyAutoIncrementTableCanUseTheInitialCounter() throws SQLException {
        latest.setId(0L);
        hasRows = false;
        coldCounter = null;
        openedCounter = null;
        assertDoesNotThrow(allocator::verifyCounter);
        assertTrue(tableOpened);
        verifyRestoredAndClosed();
    }

    @Test
    void nonemptyTableWithMissingStatisticsCannotGuessTheNextId() throws SQLException {
        coldCounter = null;
        openedCounter = null;
        assertThrows(IllegalStateException.class, allocator::verifyCounter);
        assertTrue(tableOpened);
        verifyRestoredAndClosed();
    }

    @Test
    void missingTableStatisticsCannotUseTheInitialCounter() throws SQLException {
        counterExists = false;
        latest.setId(0L);
        hasRows = false;
        assertThrows(IllegalStateException.class, allocator::verifyCounter);
        verifyRestoredAndClosed();
    }

    @Test
    void openingFailureRestoresTheOriginalConnectionSetting() throws SQLException {
        failingSql = OPEN_TABLE_SQL;
        assertThrows(DataAccessException.class, allocator::verifyCounter);
        verifyRestoredAndClosed();
    }

    @Test
    void metadataFailureRestoresTheOriginalConnectionSetting() throws SQLException {
        failingSql = COUNTER_SQL;
        assertThrows(DataAccessException.class, allocator::verifyCounter);
        verifyRestoredAndClosed();
    }

    private ResultSet query(String sql) throws SQLException {
        statements.add(sql);
        if (sql.equals(failingSql)) {
            throw new SQLException("isolated metadata failure", "HY000");
        }
        if (sql.equals("SELECT @@session.information_schema_stats_expiry")) {
            return row(true, ORIGINAL_STATS_EXPIRY);
        }
        if (sql.equals("SELECT EXTRA FROM information_schema.columns "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='judge_dispatch' AND COLUMN_NAME='id'")) {
            return row(columnExists, autoIncrement ? "auto_increment" : "");
        }
        if (sql.equals(OPEN_TABLE_SQL)) {
            tableOpened = true;
            return row(hasRows, 7L);
        }
        if (sql.equals(COUNTER_SQL)) {
            return row(counterExists, tableOpened ? openedCounter : coldCounter);
        }
        throw new AssertionError("Unexpected SQL in allocator test: " + sql);
    }

    private ResultSet row(boolean present, Object value) throws SQLException {
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(present, false);
        when(result.getString(1)).thenReturn(value == null ? null : value.toString());
        when(result.getLong(1)).thenReturn(value instanceof Number number ? number.longValue() : 0L);
        when(result.wasNull()).thenReturn(value == null);
        return result;
    }

    private void verifyRestoredAndClosed() throws SQLException {
        assertEquals("SET SESSION information_schema_stats_expiry=" + ORIGINAL_STATS_EXPIRY,
                statements.get(statements.size() - 1));
        assertTrue(statements.contains("SET SESSION information_schema_stats_expiry=0"));
        assertTrue(statements.indexOf("SET SESSION information_schema_stats_expiry=0")
                < statements.indexOf(COUNTER_SQL) || !statements.contains(COUNTER_SQL));
        verify(statement).close();
        verify(connection).close();
    }
}
