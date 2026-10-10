package com.ymcloud.web;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.StorageDirectoryResolver;
import com.ymcloud.core.storage.StorageProperties;
import com.ymcloud.core.storage.StorageRegistry;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.storage.TestDataServiceImpl;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.TestDataVersionEntityService;
import com.ymcloud.pojo.bo.TestDataManifest;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.FileBizType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 全部目录和字节均位于临时工作区，不修改 user.dir 或访问数据库及消息服务。 */
class LocalStorageDirectoryTest {

    @TempDir
    private Path directory;

    @Test
    void localStartupAutomaticallyCreatesPublicAndPrivateDirectoriesWithoutRemoteCredentials() {
        Path root = directory.resolve("new-install/files");
        StorageProperties properties = new StorageProperties();
        assertTrue(properties.getProfiles().isEmpty());
        assertNull(properties.getConfigEncryptionKey());
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(StorageRegistry.class, () -> new StorageRegistry(properties, root.toString()));
            context.refresh();
            StorageRegistry registry = context.getBean(StorageRegistry.class);
            assertEquals("local", registry.active());
            assertEquals(root.toAbsolutePath().normalize(), registry.localRoot());
            assertTrue(Files.isDirectory(root.resolve("public")));
            assertTrue(Files.isDirectory(root.resolve("private")));
        }
    }

    @Test
    void relativeDirectoriesResolveToSameRepositoryRootFromRootModuleAndTarget() throws Exception {
        Path repository = repository("checkout");
        Path applicationDirectory = Files.createDirectories(repository.resolve("ymcloud-web/target/classes"));
        List<Path> workingDirectories = List.of(repository, repository.resolve("ymcloud-web"),
                repository.resolve("ymcloud-web/target"), applicationDirectory);
        for (Path workingDirectory : workingDirectories) {
            assertEquals(repository.resolve("files"),
                    StorageDirectoryResolver.resolve("./files", workingDirectory, applicationDirectory),
                    "Launching from a module or target must not create another files directory.");
        }
        assertFalse(Files.exists(repository.resolve("files")), "Resolution alone must not create storage directories.");
    }

    @Test
    void nullApplicationDirectoryUsesWorkingDirectoryRepositoryAncestors() throws Exception {
        Path repository = repository("working-checkout");
        for (String module : List.of("ymcloud-core", "ymcloud-web", "ymcloud-judge")) {
            Path target = Files.createDirectories(repository.resolve(module).resolve("target/classes"));
            assertEquals(repository.resolve("files"), StorageDirectoryResolver.resolve("files", target, null));
        }
    }

    @Test
    void applicationCheckoutWinsWhenWorkingDirectoryBelongsToAnotherCheckout() throws Exception {
        Path applicationCheckout = repository("application-checkout");
        Path otherCheckout = repository("other-checkout");
        Path applicationDirectory = Files.createDirectories(applicationCheckout.resolve("ymcloud-web/target"));
        assertEquals(applicationCheckout.resolve("files"),
                StorageDirectoryResolver.resolve("files", otherCheckout.resolve("ymcloud-web"), applicationDirectory));
    }

    @Test
    void standaloneJarDeploymentDirectoryWinsOverUnrelatedWorkingRepository() throws Exception {
        Path workingCheckout = repository("shell-checkout");
        Path deploymentDirectory = Files.createDirectories(directory.resolve("standalone-release"));
        assertEquals(deploymentDirectory.resolve("files"),
                StorageDirectoryResolver.resolve("files", workingCheckout.resolve("ymcloud-web/target"), deploymentDirectory));
        assertFalse(Files.exists(deploymentDirectory.resolve("files")));
        assertFalse(Files.exists(workingCheckout.resolve("files")));
    }

    @Test
    void noDeploymentOrRepositoryFallsBackToExplicitWorkingDirectory() throws Exception {
        Path workingDirectory = Files.createDirectories(directory.resolve("plain-working-directory"));
        assertEquals(workingDirectory.resolve("nested/files"),
                StorageDirectoryResolver.resolve("nested/files", workingDirectory, null));
    }

    @Test
    void absoluteOverrideIsNormalizedAndIndependentOfCheckoutAndDeployment() throws Exception {
        Path repository = repository("override-checkout");
        Path deploymentDirectory = Files.createDirectories(directory.resolve("override-release"));
        Path configured = directory.resolve("explicit/unused/../files");
        Path expected = directory.resolve("explicit/files").toAbsolutePath().normalize();
        assertEquals(expected, StorageDirectoryResolver.resolve(configured.toString(), repository, deploymentDirectory));
        assertEquals(expected, StorageDirectoryResolver.resolve(configured.toString(), deploymentDirectory, null));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(StorageRegistry.class,
                    () -> new StorageRegistry(new StorageProperties(), configured.toString()));
            context.refresh();
            assertEquals(expected, context.getBean(StorageRegistry.class).localRoot());
            assertTrue(Files.isDirectory(expected.resolve("public")));
            assertTrue(Files.isDirectory(expected.resolve("private")));
        }
    }

    @Test
    void rootAndAncestorFilesCannotBeReplacedByStorageDirectories() throws Exception {
        byte[] original = "existing-file\r\n".getBytes(StandardCharsets.UTF_8);
        Path rootFile = directory.resolve("file-instead-of-root");
        Files.write(rootFile, original);
        assertThrows(FileException.class, () -> StorageDirectoryResolver.ensureDirectories(rootFile));
        assertArrayEquals(original, Files.readAllBytes(rootFile));
        assertThrows(FileException.class, () -> StorageDirectoryResolver.ensureDirectories(rootFile.resolve("files")));
        assertArrayEquals(original, Files.readAllBytes(rootFile));
    }

    @Test
    void publicAndPrivateFilesCannotBeReplacedByBucketDirectories() throws Exception {
        byte[] original = "keep-existing-bytes".getBytes(StandardCharsets.UTF_8);
        for (String bucket : List.of("public", "private")) {
            Path root = Files.createDirectories(directory.resolve("blocked-" + bucket));
            Path existingFile = root.resolve(bucket);
            Files.write(existingFile, original);
            assertThrows(FileException.class, () -> StorageDirectoryResolver.ensureDirectories(root));
            assertArrayEquals(original, Files.readAllBytes(existingFile));
        }
    }

    @Test
    void initializationRejectsRootBucketAndAncestorSymbolicLinks() throws Exception {
        Path outside = Files.createDirectories(directory.resolve("link-target"));
        Path rootLink = directory.resolve("root-link");
        createSymbolicLinkOrSkip(rootLink, outside);
        assertThrows(FileException.class, () -> StorageDirectoryResolver.ensureDirectories(rootLink));
        assertThrows(FileException.class, () -> StorageDirectoryResolver.ensureDirectories(rootLink.resolve("nested-files")));
        for (String bucket : List.of("public", "private")) {
            Path root = Files.createDirectories(directory.resolve("linked-" + bucket));
            createSymbolicLinkOrSkip(root.resolve(bucket), outside);
            assertThrows(FileException.class, () -> StorageDirectoryResolver.ensureDirectories(root));
        }
        try (var contents = Files.list(outside)) {
            assertEquals(0, contents.count(), "Rejected link targets must not receive directories or objects.");
        }
    }

    @Test
    void repeatedInitializationPreservesExistingPublicAndLegacyPrivateBytes() throws Exception {
        Path root = Files.createDirectories(directory.resolve("existing/files"));
        Path publicFile = root.resolve("public/old-avatar.png");
        Path legacyFile = root.resolve("private/problem/42/in/a.in");
        Files.createDirectories(publicFile.getParent());
        Files.createDirectories(legacyFile.getParent());
        byte[] publicBytes = new byte[]{0, 1, 2, (byte) 255};
        byte[] privateBytes = "  中文输入\r\n\t ".getBytes(StandardCharsets.UTF_8);
        Files.write(publicFile, publicBytes);
        Files.write(legacyFile, privateBytes);

        StorageDirectoryResolver.ensureDirectories(root);
        StorageDirectoryResolver.ensureDirectories(root);

        assertArrayEquals(publicBytes, Files.readAllBytes(publicFile));
        assertArrayEquals(privateBytes, Files.readAllBytes(legacyFile));
        try (var contents = Files.walk(root)) {
            assertEquals(2, contents.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void localPublicPrivateAndLegacyTestDataUseOneResolvedBase() throws Exception {
        Path repository = repository("shared-root-checkout");
        Path moduleTarget = Files.createDirectories(repository.resolve("ymcloud-web/target/classes"));
        Path root = StorageDirectoryResolver.resolve("files", moduleTarget, moduleTarget);
        StorageRegistry registry = new StorageRegistry(new StorageProperties(), root.toString());
        registry.validateActiveProfile();
        FileEntityService metadata = mock(FileEntityService.class);
        AtomicLong ids = new AtomicLong(100);
        when(metadata.save(any(File.class))).thenAnswer(invocation -> {
            invocation.<File>getArgument(0).setId(ids.incrementAndGet());
            return true;
        });
        StoredFileService storage = new StoredFileService(metadata, registry);
        byte[] publicBytes = "图片文件\n".getBytes(StandardCharsets.UTF_8);
        byte[] privateBytes = " \r\n私有输入\t ".getBytes(StandardCharsets.UTF_8);
        try {
            File publicFile = storage.save(new ByteArrayInputStream(publicBytes), publicBytes.length,
                    "public.png", "image/png", FileBizType.IMAGE, null, true);
            File privateFile = storage.save(new ByteArrayInputStream(privateBytes), privateBytes.length,
                    "private.in", "text/plain", FileBizType.TEST_DATA, 42L, false);
            assertEquals("local", publicFile.getStorageProfile());
            assertEquals("public", publicFile.getBucket());
            assertTrue(publicFile.getUrl().startsWith("/files/public/local/"));
            assertEquals("local", privateFile.getStorageProfile());
            assertEquals("private", privateFile.getBucket());
            assertNull(privateFile.getUrl());
            try (InputStream input = storage.open(publicFile)) {
                assertArrayEquals(publicBytes, input.readAllBytes());
            }
            try (InputStream input = storage.open(privateFile)) {
                assertArrayEquals(privateBytes, input.readAllBytes());
            }
            assertArrayEquals(publicBytes, Files.readAllBytes(root.resolve(publicFile.getStoragePath())));
            assertArrayEquals(privateBytes, Files.readAllBytes(root.resolve(privateFile.getStoragePath())));

            Path legacyRoot = root.resolve("private/problem/42");
            Files.createDirectories(legacyRoot.resolve("in"));
            Files.createDirectories(legacyRoot.resolve("out"));
            Files.write(legacyRoot.resolve("in/a.in"), privateBytes);
            Files.writeString(legacyRoot.resolve("out/a.out"), "answer\n", StandardCharsets.UTF_8);
            Path wrongModuleRoot = Files.createDirectories(moduleTarget.resolve("files/private/problem/42/in"));
            Files.writeString(wrongModuleRoot.resolve("a.in"), "wrong module input", StandardCharsets.UTF_8);
            TestDataVersionEntityService versions = mock(TestDataVersionEntityService.class);
            TestDataServiceImpl testData = new TestDataServiceImpl(problemService(42L), versions, storage, root.toString());
            TestDataManifest manifest = testData.getManifest(42L, null);
            assertNull(manifest.getVersionId());
            assertEquals(1, manifest.getEntries().size());
            try (InputStream input = testData.openFile(42L, null, "/in/a.in")) {
                assertArrayEquals(privateBytes, input.readAllBytes());
            }
            try (InputStream output = testData.openFile(42L, null, "/out/a.out")) {
                assertEquals("answer\n", new String(output.readAllBytes(), StandardCharsets.UTF_8));
            }
            verifyNoInteractions(versions);
        } finally {
            registry.close();
        }
    }

    private Path repository(String name) throws IOException {
        Path root = Files.createDirectories(directory.resolve(name));
        Files.writeString(root.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.ymcloud</groupId>
                  <artifactId>ymcloud</artifactId>
                  <packaging>pom</packaging>
                  <modules>
                    <module>ymcloud-core</module>
                    <module>ymcloud-web</module>
                    <module>ymcloud-judge</module>
                  </modules>
                </project>
                """, StandardCharsets.UTF_8);
        for (String module : List.of("ymcloud-core", "ymcloud-web", "ymcloud-judge")) {
            Path moduleDirectory = Files.createDirectories(root.resolve(module));
            Files.writeString(moduleDirectory.resolve("pom.xml"), """
                    <project>
                      <parent><artifactId>ymcloud</artifactId></parent>
                      <artifactId>%s</artifactId>
                    </project>
                    """.formatted(module), StandardCharsets.UTF_8);
        }
        return root.toAbsolutePath().normalize();
    }

    @SuppressWarnings("unchecked")
    private ProblemEntityService problemService(long id) {
        ProblemEntityService problems = mock(ProblemEntityService.class);
        LambdaQueryChainWrapper<Problem> query = mock(LambdaQueryChainWrapper.class);
        Problem problem = new Problem();
        problem.setId(id);
        when(problems.lambdaQuery()).thenReturn(query);
        // 泛型链式方法的返回类型会擦除为 Object，显式绑定 eq，避免默认答案返回 null。
        when(query.eq(any(), any())).thenReturn(query);
        when(query.one()).thenReturn(problem);
        return problems;
    }

    private void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            assumeTrue(false, "The host does not allow creating symbolic-link fixtures.");
        }
    }
}
