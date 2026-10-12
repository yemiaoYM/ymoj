package com.ymcloud.judge;

import com.ymcloud.judge.bo.CmdExecResult;
import com.ymcloud.judge.docker.container.CodeExecContainer;
import com.ymcloud.judge.exception.CompileException;
import com.ymcloud.judge.exception.MemoryLimitExceededException;
import com.ymcloud.judge.exception.RuntimeErrorException;
import com.ymcloud.judge.exception.SandBoxException;
import com.ymcloud.judge.exception.TimeLimitExceededException;
import com.ymcloud.judge.sandbox.CSandBox;
import com.ymcloud.judge.sandbox.SandBox;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SandboxClassificationTest {

    @Test
    void compileUsesExitCodeAndWarningsAreNotCompileErrors() throws Exception {
        CodeExecContainer container = mock(CodeExecContainer.class);
        SandBox sandbox = new CSandBox(container);
        when(container.execCmd(any(String[].class), anyLong())).thenReturn(command(0, "warning"));
        assertDoesNotThrow(() -> sandbox.compile(container));
        when(container.execCmd(any(String[].class), anyLong())).thenReturn(command(1, ""));
        assertThrows(CompileException.class, () -> sandbox.compile(container));
        when(container.execCmd(any(String[].class), anyLong())).thenThrow(new IllegalStateException("daemon offline"));
        assertThrows(SandBoxException.class, () -> sandbox.compile(container));
    }

    @Test
    void classifiesTleCgroupOomAndRuntimeSeparatelyAndReportsMillisecondsKiB() throws Exception {
        CodeExecContainer container = mock(CodeExecContainer.class);
        SandBox sandbox = new CSandBox(container);
        when(container.isRunning()).thenReturn(true);
        when(container.oomKillCount()).thenReturn(0L);
        when(container.execCmd(any(String[].class), any(InputStream.class), anyLong())).thenReturn(command(0, "__TIME__:0.025 512"));
        var result = sandbox.execute(container, "", 1000L);
        assertEquals(25L, result.getTimeUsed());
        assertEquals(512L, result.getMemoryUsed());
        when(container.execCmd(any(String[].class), any(InputStream.class), anyLong())).thenReturn(command(124, ""));
        assertThrows(TimeLimitExceededException.class, () -> sandbox.execute(container, "", 1000L));
        when(container.execCmd(any(String[].class), any(InputStream.class), anyLong())).thenReturn(command(3, ""));
        assertThrows(RuntimeErrorException.class, () -> sandbox.execute(container, "", 1000L));
        when(container.oomKillCount()).thenReturn(0L, 1L);
        when(container.execCmd(any(String[].class), any(InputStream.class), anyLong())).thenReturn(command(137, ""));
        assertThrows(MemoryLimitExceededException.class, () -> sandbox.execute(container, "", 1000L));
    }

    private CmdExecResult command(long exit, String stderr) {
        return CmdExecResult.builder().stdout("").stderr(stderr).exitCode(exit).build();
    }
}
