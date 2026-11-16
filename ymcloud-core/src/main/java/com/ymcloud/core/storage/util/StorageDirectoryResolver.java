package com.ymcloud.core.storage.util;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.exception.file.FileException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.boot.system.ApplicationHome;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 相对存储目录以聚合项目根或独立部署目录为基准，避免多模块启动产生多个文件根
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StorageDirectoryResolver {

    private static final Set<String> REQUIRED_MODULES = Set.of("ymcloud-core", "ymcloud-web", "ymcloud-judge");
    private static volatile Path applicationDirectoryHint;

    /**
     * 根据配置与应用代码位置解析存储根目录
     * @param configuredPath 绝对目录或相对于项目根、部署目录的路径
     * @return 规范化的绝对存储根目录
     * @throws FileException 目录配置为空、非法或基准目录无法确定
     */
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
     * 代码目录优先：复制到独立目录的 jar 不会因为从另一个仓库启动而写入那个仓库
     * 未提供代码目录时从工作目录向上寻找聚合根，绝对配置保持其指定位置
     * @param configuredPath 存储目录配置
     * @param workingDirectory 代码目录不可用时的工作目录，可为空
     * @param applicationDirectory 优先使用的代码目录，可为空
     * @return 规范化的绝对存储根目录
     * @throws FileException 配置非法或两个基准目录均不可用
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
     * 仅创建缺失的公开和私有目录，不覆盖文件，也不允许符号链接改变实际位置
     * @param root 已解析的本地存储根目录
     * @throws FileException 目录路径不安全、被文件占用或无法创建
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

    /**
     * 检查目录及祖先，避免目录被普通文件或符号链接占用
     * @param directory 待创建或使用的目录
     * @throws FileException 目录或祖先节点类型不合法
     */
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

    /**
     * 从主类代码来源确定部署目录，并缓存供异步调用使用
     * @return 应用代码所在目录，无法识别时为空
     */
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
        // 无 main 栈的异步判题仍使用启动时确定的位置
        // 依赖缓存目录不会成为部署根
        if (directory != null) {
            applicationDirectoryHint = directory;
        }
        return directory;
    }

    /**
     * 获取存在代码来源的应用目录
     * @param home Spring 解析的应用位置
     * @return 代码所属目录，来源不可识别时为空
     */
    private static Path sourceDirectory(ApplicationHome home) {
        return home.getSource() == null ? null : home.getDir().toPath();
    }

    /**
     * 逐级向上寻找包含必需模块的项目聚合根
     * @param directory 搜索起始目录
     * @return 最近的项目根，未找到时为空
     */
    private static Path findProjectRoot(Path directory) {
        for (Path cursor = directory; cursor != null; cursor = cursor.getParent()) {
            if (isProjectRoot(cursor)) {
                return cursor;
            }
        }
        return null;
    }

    /**
     * 检查目录及 POM 是否属于本项目聚合根，不加载外部 XML 资源
     * @param directory 候选目录
     * @return 目录结构和 POM 声明都匹配时为 true
     * @throws FileException 候选项目的 POM 无法读取或解析
     */
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
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new FileException("项目目录配置读取失败", e);
        }
    }

    /**
     * 查找指定名称的直接子元素，不匹配更深层同名节点
     * @param parent 父元素
     * @param name 目标元素本地名称
     * @return 第一个匹配子元素，不存在时为空
     */
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
