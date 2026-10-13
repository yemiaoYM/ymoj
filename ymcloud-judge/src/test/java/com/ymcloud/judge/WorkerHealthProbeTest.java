package com.ymcloud.judge;

import com.ymcloud.judge.config.WorkerHealthProbe;
import com.ymcloud.judge.config.WorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerHealthProbeTest {

    @TempDir
    Path directory;

    private RabbitListenerEndpointRegistry registry;
    private SimpleMessageListenerContainer container;
    private WorkerHealthProbe probe;
    private Path health;

    @BeforeEach
    void setup() {
        registry = mock(RabbitListenerEndpointRegistry.class);
        container = mock(SimpleMessageListenerContainer.class);
        WorkerProperties properties = new WorkerProperties();
        properties.setWorkDir(directory);
        properties.setConcurrency(2);
        probe = new WorkerHealthProbe(registry, properties);
        health = directory.resolve("health");
        when(registry.getListenerContainers()).thenReturn(List.of(container));
    }

    @Test
    void startupRemovesStaleHealthAndOnlyReadyConsumersProduceFreshAtomicMarker() throws Exception {
        Files.writeString(health, "1", StandardCharsets.UTF_8);
        probe.initialize();
        assertFalse(Files.exists(health));
        when(container.isRunning()).thenReturn(true);
        when(container.getActiveConsumerCount()).thenReturn(2);
        probe.refresh();
        assertFalse(Files.exists(health));
        long started = System.currentTimeMillis();
        probe.applicationReady();
        long timestamp = Long.parseLong(Files.readString(health, StandardCharsets.UTF_8).trim());
        assertTrue(timestamp >= started && timestamp <= System.currentTimeMillis());
        try (var files = Files.list(directory)) {
            assertEquals(List.of("health"), files.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    void lostConsumerStoppedListenerOrMissingRegistrationCannotRemainHealthy() {
        when(container.isRunning()).thenReturn(true);
        when(container.getActiveConsumerCount()).thenReturn(2);
        probe.applicationReady();
        assertTrue(Files.exists(health));
        when(container.getActiveConsumerCount()).thenReturn(1);
        probe.refresh();
        assertFalse(Files.exists(health));
        when(container.getActiveConsumerCount()).thenReturn(2);
        probe.refresh();
        assertTrue(Files.exists(health));
        when(container.isRunning()).thenReturn(false);
        probe.refresh();
        assertFalse(Files.exists(health));
        when(registry.getListenerContainers()).thenReturn(List.of());
        probe.refresh();
        assertFalse(Files.exists(health));
    }

    @Test
    void everyListenerMustBeReadyAndInspectionFailureRemovesPreviousMarker() {
        when(container.isRunning()).thenReturn(true);
        when(container.getActiveConsumerCount()).thenReturn(2);
        probe.applicationReady();
        MessageListenerContainer other = mock(MessageListenerContainer.class);
        when(registry.getListenerContainers()).thenReturn(List.of(container, other));
        probe.refresh();
        assertFalse(Files.exists(health));
        when(registry.getListenerContainers()).thenReturn(List.of(container));
        probe.refresh();
        assertTrue(Files.exists(health));
        when(registry.getListenerContainers()).thenThrow(new IllegalStateException("unavailable"));
        assertDoesNotThrow(probe::refresh);
        assertFalse(Files.exists(health));
    }

    @Test
    void shutdownRemovesMarkerAndScheduledRefreshCannotRecreateIt() {
        when(container.isRunning()).thenReturn(true);
        when(container.getActiveConsumerCount()).thenReturn(2);
        probe.applicationReady();
        assertTrue(Files.exists(health));
        probe.close();
        probe.refresh();
        assertFalse(Files.exists(health));
    }

    @Test
    void invalidWorkDirectoryDoesNotInterruptMessageProcessing() throws Exception {
        Path file = Files.writeString(directory.resolve("not-a-directory"), "preserve", StandardCharsets.UTF_8);
        WorkerProperties properties = new WorkerProperties();
        properties.setWorkDir(file);
        WorkerHealthProbe invalid = new WorkerHealthProbe(registry, properties);
        when(container.isRunning()).thenReturn(true);
        when(container.getActiveConsumerCount()).thenReturn(2);
        assertDoesNotThrow(invalid::applicationReady);
        assertEquals("preserve", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void finalSymlinkIsRemovedWithoutOverwritingItsExternalTarget() throws Exception {
        Path external = Files.writeString(directory.resolve("external"), "preserve", StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(health, external);
        } catch (IOException | UnsupportedOperationException deniedLinks) {
            assumeTrue(false, "当前平台不允许测试创建符号链接");
        }
        when(container.isRunning()).thenReturn(true);
        when(container.getActiveConsumerCount()).thenReturn(2);
        assertDoesNotThrow(probe::applicationReady);
        assertFalse(Files.exists(health));
        assertEquals("preserve", Files.readString(external, StandardCharsets.UTF_8));
    }
}
