package com.ymcloud.core.storage;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.exception.file.FileException;
import org.springframework.boot.system.ApplicationHome;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * 相对存储目录以聚合项目根或独立部署目录为基准，避免多模块启动产生多个文件根。
 */
public final class StorageDirectoryResolver {

    private static final Set<String> REQUIRED_MODULES = Set.of("ymcloud-core", "ymcloud-web", "ymcloud-judge");
    private static volatile Path applicationDirectoryHint;

    private StorageDirectoryResolver() {
    }

    public static Path resolve(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new FileException("本地目录未配置");
        }
        try {
            Path configured = Path.of(configuredPath);
            if (configured.isAbsolute()) {
                return configured.normalize();
            }
            return resolve(configuredPath, Path.of(System.getProperty("user.dir", ".")), applicationDirectory());
        } catch (InvalidPathException e) {
            throw new FileException("本地目录配置无效", e);
        }
    }

    /**
     * 代码目录优先：复制到独立目录的 jar 不会因为从另一个仓库启动而写入那个仓库。
     * 未提供代码目录时从工作目录向上寻找聚合根；绝对配置始终保持其指定位置。
     */
    public static Path resolve(String configuredPath, Path workingDirectory, Path applicationDirectory) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new FileException("本地目录未配置");
        }
        try {
            Path configured = Path.of(configuredPath);
            if (configured.isAbsolute()) {
                return configured.normalize();
            }
            Path base = applicationDirectory;
            if (base == null) {
                base = workingDirectory;
            }
            if (base == null) {
                throw new FileException("无法确定本地存储根目录");
            }
            base = base.toAbsolutePath().normalize();
            if (Files.isRegularFile(base)) {
                base = base.getParent();
            }
            Path projectRoot = findProjectRoot(base);
            return (projectRoot == null ? base : projectRoot).resolve(configured).normalize();
        } catch (InvalidPathException e) {
            throw new FileException("本地目录配置无效", e);
        }
    }

    /**
     * 仅创建缺失的公开和私有目录，不覆盖文件，也不允许符号链接改变实际位置。
     */
    public static void ensureDirectories(Path root) {
        Path base = root.toAbsolutePath().normalize();
        Path publicDirectory = StoragePaths.resolve(base, FileConstants.DIR_PUBLIC);
        Path privateDirectory = StoragePaths.resolve(base, FileConstants.DIR_PRIVATE);
        validateDirectoryAncestors(publicDirectory);
        validateDirectoryAncestors(privateDirectory);
        try {
            Files.createDirectories(publicDirectory);
            Files.createDirectories(privateDirectory);
            StoragePaths.resolve(base, FileConstants.DIR_PUBLIC);
            StoragePaths.resolve(base, FileConstants.DIR_PRIVATE);
        } catch (IOException e) {
            throw new FileException("无法创建本地文件目录", e);
        }
    }

    private static void validateDirectoryAncestors(Path directory) {
        for (Path cursor = directory; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) {
                throw new FileException("本地文件目录不允许符号链接");
            }
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileException("本地文件目录被普通文件占用");
            }
        }
    }

    private static Path applicationDirectory() {
        Path directory = applicationDirectoryHint;
        if (directory != null) {
            return directory;
        }
        Class<?> mainClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .filter(frame -> "main".equals(frame.getMethodName()))
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(null));
        if (mainClass != null) {
            directory = sourceDirectory(new ApplicationHome(mainClass));
        }
        if (directory == null) {
            directory = sourceDirectory(new ApplicationHome());
        }
        if (directory == null) {
            Path helperDirectory = sourceDirectory(new ApplicationHome(StorageDirectoryResolver.class));
            if (helperDirectory != null && findProjectRoot(helperDirectory) != null) {
                directory = helperDirectory;
            }
        }
        // 无 main 栈的异步判题仍使用启动时确定的位置；依赖缓存目录不会成为部署根。
        if (directory != null) {
            applicationDirectoryHint = directory;
        }
        return directory;
    }

    private static Path sourceDirectory(ApplicationHome home) {
        return home.getSource() == null ? null : home.getDir().toPath();
    }

    private static Path findProjectRoot(Path directory) {
        for (Path cursor = directory; cursor != null; cursor = cursor.getParent()) {
            if (isProjectRoot(cursor)) {
                return cursor;
            }
        }
        return null;
    }

    private static boolean isProjectRoot(Path directory) {
        Path pom = directory.resolve("pom.xml");
        if (!Files.isRegularFile(pom, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        for (String module : REQUIRED_MODULES) {
            if (!Files.isDirectory(directory.resolve(module), LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
        }
        try (InputStream input = Files.newInputStream(pom)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Element project = builder.parse(input).getDocumentElement();
            Element artifactId = child(project, "artifactId");
            Element packaging = child(project, "packaging");
            Element modules = child(project, "modules");
            if (artifactId == null || !"ymcloud".equals(artifactId.getTextContent().trim())
                    || packaging == null || !"pom".equals(packaging.getTextContent().trim()) || modules == null) {
                return false;
            }
            Set<String> declaredModules = new HashSet<>();
            NodeList children = modules.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node node = children.item(index);
                if (node instanceof Element element && "module".equals(element.getLocalName())) {
                    declaredModules.add(element.getTextContent().trim());
                }
            }
            return declaredModules.containsAll(REQUIRED_MODULES);
        } catch (Exception e) {
            return false;
        }
    }

    private static Element child(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }
}
