package com.ymcloud.web.remote;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 定位题面中的实际图片，仅替换图片节点，保持公式和代码原文
 */
public final class ProblemImageReferences {

    private static final Parser MARKDOWN = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    /**
     * 工具类型不允许实例化。
     */
    private ProblemImageReferences() {}

    /**
     * 提取 Markdown 和 HTML 图片，代码中的图片示例不参与下载。
     * @param markdown 平台返回的题面段落
     * @return 按源码位置排列的图片引用
     */
    public static List<Reference> find(String markdown) {
        List<Reference> images = new ArrayList<>();
        MARKDOWN.parse(markdown).accept(new AbstractVisitor() {
            /**
             * 记录内联或引用式图片，不将其替代文字重新解析成图片。
             * @param image Markdown 图片节点
             */
            @Override
            public void visit(Image image) {
                StringBuilder alt = new StringBuilder();
                image.accept(new AbstractVisitor() {
                    /**
                     * 保留图片的替代文字。
                     * @param text 图片标签中的文字节点
                     */
                    @Override
                    public void visit(Text text) {
                        alt.append(text.getLiteral());
                    }
                });
                List<SourceSpan> spans = image.getSourceSpans();
                SourceSpan last = spans.get(spans.size() - 1);
                images.add(new Reference(spans.get(0).getInputIndex(), last.getInputIndex() + last.getLength(),
                        image.getDestination(), alt.toString(), image.getTitle(), false));
            }

            /**
             * 记录行内 HTML 图片。
             * @param node 行内 HTML 节点
             */
            @Override
            public void visit(HtmlInline node) {
                htmlImages(markdown, node, images);
            }

            /**
             * 记录 HTML 区域内的实际图片。
             * @param node HTML 块节点
             */
            @Override
            public void visit(HtmlBlock node) {
                htmlImages(markdown, node, images);
            }
        });
        images.sort(Comparator.comparingInt(Reference::start));
        return images;
    }

    /**
     * 使用 HTML 源码位置保留块内其他文字及格式。
     * @param markdown 完整原文
     * @param node HTML 节点
     * @param images 图片引用收集器
     */
    private static void htmlImages(String markdown, Node node, List<Reference> images) {
        List<SourceSpan> spans = node.getSourceSpans();
        int offset = spans.get(0).getInputIndex();
        SourceSpan last = spans.get(spans.size() - 1);
        String html = markdown.substring(offset, last.getInputIndex() + last.getLength());
        var document = org.jsoup.parser.Parser.htmlParser().setTrackPosition(true).parseInput(html, "");
        for (Element image : document.select("img[src]")) {
            if (image.parents().stream().anyMatch(parent -> parent.is("script, style"))) {
                continue;
            }
            var range = image.sourceRange();
            images.add(new Reference(offset + range.start().pos(), offset + range.end().pos(),
                    image.attr("src"), image.attr("alt"), image.attr("title"), true));
        }
    }

    /**
     * 从后向前替换源码片段，其他字符、数学语法和样例保持原样。
     * @param markdown 原文
     * @param images 本段图片引用
     * @param urls 原图片地址到本站地址的映射
     * @return 已替换图片地址的题面
     */
    public static String replace(String markdown, List<Reference> images, Map<String, String> urls) {
        StringBuilder result = new StringBuilder(markdown);
        for (int index = images.size() - 1; index >= 0; index--) {
            Reference image = images.get(index);
            if (image.formula() != null) {
                result.replace(image.start(), image.end(), "$" + image.formula() + "$");
                continue;
            }
            String url = urls.get(image.url());
            Element tag = new Element("img").attr("src", url).attr("alt", image.alt());
            if (image.title() != null && !image.title().isEmpty()) {
                tag.attr("title", image.title());
            }
            String replacement = tag.outerHtml();
            if (!image.html()) {
                String alt = image.alt().replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
                        .replace("\n", " ");
                String title = image.title() == null || image.title().isEmpty() ? "" : " \"" + image.title()
                        .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
                replacement = "![" + alt + "](" + url + title + ")";
            }
            result.replace(image.start(), image.end(), replacement);
        }
        return result.toString();
    }

    /**
     * @Author: Yemiao
     * @CreateTime: 2026-10-29
     * @Description: 单张图片在原始题面中的位置及展示属性
     */
    public record Reference(int start, int end, String url, String alt, String title, boolean html) {
        /**
         * 识别已知平台公式图片，普通插图交由下载存储。
         * @return LaTeX 原文；普通插图返回 null
         */
        public String formula() {
            return ProblemHtmlMarkdown.equationImage(url, alt);
        }
    }
}
