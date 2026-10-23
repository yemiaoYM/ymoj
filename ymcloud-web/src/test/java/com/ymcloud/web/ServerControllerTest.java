package com.ymcloud.web;

import com.ymcloud.common.result.Result;
import com.ymcloud.core.web.domain.Server;
import com.ymcloud.web.controller.admin.monitor.ServerController;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;

class ServerControllerTest {

    private final ServerController controller = new ServerController();

    @Test
    void successfulCollectionReturnsServer() {
        try (MockedConstruction<Server> servers = mockConstruction(Server.class)) {
            Result<Server> result = controller.getInfo();
            assertEquals(Result.SUCCESS, result.getCode());
            assertSame(servers.constructed().get(0), result.getData());
        }
    }

    @Test
    void nativeLoadAndCollectionFailuresReturnFailureWithoutDiagnosticLeak() {
        Throwable[] failures = {
                new UnsatisfiedLinkError("private native path /jna-tmp/library.so"),
                new NoClassDefFoundError("Could not initialize class oshi.hardware.platform.linux.LinuxGlobalMemory"),
                new IllegalStateException("private collection details")
        };
        for (Throwable failure : failures) {
            try (MockedConstruction<Server> ignored = mockConstruction(Server.class,
                    (server, context) -> doThrow(failure).when(server).copyTo())) {
                Result<Server> result = controller.getInfo();
                assertEquals(Result.FAIL, result.getCode());
                assertNull(result.getData());
                assertFalse(result.getMsg().contains(failure.getMessage()));
            }
        }
    }
}
