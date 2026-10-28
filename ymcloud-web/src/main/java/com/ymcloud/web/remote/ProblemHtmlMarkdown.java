package com.ymcloud.web.remote;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.regex.Pattern;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 各平台题面 HTML 转 Markdown，保留数学公式和结构，不输出活动 HTML
 */
public final class ProblemHtmlMarkdown {

    private static final Pattern MATH = Pattern.compile("(\\${1,3})(.+?)\\1", Pattern.DOTALL);

    /**
     * 工具类型无需实例化。
     */
    private ProblemHtmlMarkdown() {}

    /**
     * 转换已选定的题面节点，链接以节点 baseUri 为基准解析。
     * @param element 题面或输入输出区域
     * @return 可直接编辑的 Markdown
     */
    public static String convert(Element element) {
        return render(element, false).strip();
    }

    /**
     * 提取样例纯文本，保留空格、空行以及 Codeforces 按行 div 的分隔。
     * @param element 样例 pre 节点
     * @return 原始样例文本，不做 trim
     */
    public static String sampleText(Element element) {
        Element copy = element.clone();
        for (Element line : copy.select("div, p")) {
            line.appendChild(new TextNode("\n"));
        }
        return copy.wholeText().replace("\r\n", "\n");
    }

    /**
     * 递归转换结构节点，跳过脚本与表单内容。
     * @param node 待转换的 DOM 节点
     * @param preformatted 是否位于包含数学变量的预格式说明中
     * @return Markdown 片段
     */
    private static String render(Node node, boolean preformatted) {
        if (node instanceof TextNode text) {
            String raw = text.getWholeText().replace('\u00a0', ' ');
            if (!preformatted) {
                raw = raw.replaceAll("\\s+", " ");
            }
            var matcher = MATH.matcher(raw);
            StringBuilder value = new StringBuilder();
            int offset = 0;
            while (matcher.find()) {
                value.append(escape(raw.substring(offset, matcher.start()), preformatted));
                String delimiter = matcher.group(1).length() == 2 ? "$$" : "$";
                value.append(delimiter).append(matcher.group(2)).append(delimiter);
                offset = matcher.end();
            }
            return value.append(escape(raw.substring(offset), preformatted)).toString();
        }
        if (!(node instanceof Element element)) {
            return "";
        }
        String tag = element.normalName();
        if (tag.matches("script|style|iframe|object|form|button|input|svg")) {
            return "";
        }
        if (tag.equals("pre")) {
            // AtCoder 输入格式中的 var 是公式
            // 实际样例由 sampleText 单独提取
            if (element.selectFirst("var") != null && element.selectFirst("code") == null) {
                StringBuilder format = new StringBuilder("\n\n");
                for (Node child : element.childNodes()) {
                    format.append(render(child, true));
                }
                return format.append("\n\n").toString();
            }
            String value = sampleText(element);
            String fence = "```";
            while (value.contains(fence)) {
                fence += "`";
            }
            return "\n\n" + fence + "\n" + value + (value.endsWith("\n") ? "" : "\n") + fence + "\n\n";
        }
        if (tag.equals("var")) {
            return "$" + element.wholeText() + "$";
        }
        if (tag.equals("code")) {
            Element copy = element.clone();
            for (Element exponent : copy.select("sup, sub")) {
                exponent.replaceWith(new TextNode((exponent.is("sup") ? "^" : "_") + exponent.text()));
            }
            String value = copy.wholeText();
            String fence = "`";
            while (value.contains(fence)) {
                fence += "`";
            }
            return fence + " " + value + " " + fence;
        }
        if (tag.equals("img")) {
            String url = safeUrl(element, "src");
            return url.isEmpty() ? escape(element.attr("alt"))
                    : "![" + escape(element.attr("alt")) + "](" + url + ")";
        }
        StringBuilder children = new StringBuilder();
        for (Node child : element.childNodes()) {
            children.append(render(child, preformatted));
        }
        String content = children.toString();
        return switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> "\n\n" + "#".repeat(Integer.parseInt(tag.substring(1))) + " " + content.strip() + "\n\n";
            case "p", "div", "section", "ul", "ol", "blockquote" -> "\n\n" + content.strip() + "\n\n";
            case "li" -> "\n" + (element.parent() != null && element.parent().is("ol") ? "1. " : "- ") + content.strip() + "\n";
            case "strong", "b" -> "**" + content.strip() + "**";
            case "em", "i" -> "*" + content.strip() + "*";
            case "s", "del" -> "~~" + content.strip() + "~~";
            case "br" -> "\n";
            case "hr" -> "\n\n---\n\n";
            case "sup" -> "$^{" + element.text() + "}$";
            case "sub" -> "$_{" + element.text() + "}$";
            case "a" -> safeUrl(element, "href").isEmpty() ? content
                    : "[" + content + "](" + safeUrl(element, "href") + ")";
            case "table" -> table(element);
            default -> content;
        };
    }

    /**
     * 将题面表格转换为 Markdown 表格，保持单元格中的换行。
     * @param element 原表格节点
     * @return Markdown 表格
     */
    private static String table(Element element) {
        StringBuilder result = new StringBuilder("\n\n");
        boolean header = true;
        for (Element row : element.select("tr")) {
            result.append('|');
            int columns = 0;
            for (Element cell : row.children()) {
                result.append(' ').append(convert(cell).replace("|", "\\|").replace("\n", "<br>"))
                        .append(" |");
                columns++;
            }
            result.append('\n');
            if (header) {
                result.append('|').append(" --- |".repeat(columns)).append('\n');
                header = false;
            }
        }
        return result.append('\n').toString();
    }

    /**
     * 转义普通文字中的 Markdown 控制字符，防止原文变为活动 HTML。
     * @param text 非数学、非代码的普通文本
     * @return 转义后的文本
     */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("`", "\\`").replace("*", "\\*")
                .replace("_", "\\_").replace("[", "\\[").replace("]", "\\]")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * 预格式说明中的空白转换为不可折叠空格和 Markdown 硬换行，不改写公式内容。
     * @param text 公式之外的普通文字
     * @param preformatted 是否保留预格式空白
     * @return 已转义且保持指定空白语义的文字
     */
    private static String escape(String text, boolean preformatted) {
        String escaped = escape(text);
        return preformatted ? escaped.replace("\r\n", "\n").replace(" ", "&nbsp;")
                .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;").replace("\n", "  \n") : escaped;
    }

    /**
     * 保留 HTTP(S) 图片或链接并解析相对路径，排除脚本及数据协议。
     * @param element 链接或图片节点
     * @param attribute URL 属性名称
     * @return 安全的绝对 URL，其他协议返回空串
     */
    private static String safeUrl(Element element, String attribute) {
        String url = element.absUrl(attribute);
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return "";
        }
        return url.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
                .replace("<", "%3C").replace(">", "%3E");
    }
}
