package com.ymcloud.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.ymcloud.judge.docker.factory.CodeExecContainerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CodeExecContainerFactoryTest {

    @TempDir
    Path directory;

    @Test
    void sandboxKeepsResourceRestrictionsAndUsesUnixWorkingDirectoryOwner() throws Exception {
        DockerClient docker = mock(DockerClient.class);
        CreateContainerCmd command = mock(CreateContainerCmd.class, RETURNS_SELF);
        CreateContainerResponse response = new CreateContainerResponse();
        response.setId("test-container");
        when(docker.createContainerCmd("oj-java-env")).thenReturn(command);
        when(command.exec()).thenReturn(response);

        new CodeExecContainerFactory(docker, directory.toString(), "/app", 131072L,
                "oj-java-env").createDockerContainer();

        ArgumentCaptor<HostConfig> configuration = ArgumentCaptor.forClass(HostConfig.class);
        verify(command).withHostConfig(configuration.capture());
        HostConfig host = configuration.getValue();
        assertEquals(134217728L, host.getMemory());
        assertEquals(host.getMemory(), host.getMemorySwap());
        assertEquals("none", host.getNetworkMode());
        assertEquals(64L, host.getPidsLimit());
        assertTrue(host.getReadonlyRootfs());
        assertArrayEquals(new Capability[]{Capability.ALL}, host.getCapDrop());
        assertEquals("rw,nosuid,nodev,noexec,size=64m", host.getTmpFs().get("/tmp"));
        assertTrue(host.getSecurityOpts().contains("no-new-privileges:true"));
        if (directory.getFileSystem().supportedFileAttributeViews().contains("unix")) {
            String owner = Files.getAttribute(directory, "unix:uid", LinkOption.NOFOLLOW_LINKS)
                    + ":" + Files.getAttribute(directory, "unix:gid", LinkOption.NOFOLLOW_LINKS);
            verify(command).withUser(owner);
        } else {
            verify(command, never()).withUser(anyString());
        }
    }

    @Test
    void invalidMountPointOrMemoryFailsBeforeCreatingAnyDockerResource() {
        DockerClient docker = mock(DockerClient.class);
        for (String path : new String[]{"/", "relative", "/app/../escape"}) {
            assertThrows(IllegalArgumentException.class, () -> new CodeExecContainerFactory(
                    docker, directory.toString(), path, 131072L, "oj-java-env"));
        }
        assertThrows(IllegalArgumentException.class, () -> new CodeExecContainerFactory(
                docker, directory.toString(), "/app", Long.MAX_VALUE, "oj-java-env"));
        verifyNoInteractions(docker);
    }
}
