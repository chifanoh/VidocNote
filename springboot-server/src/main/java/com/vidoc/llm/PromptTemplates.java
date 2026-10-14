package com.vidoc.llm;

/**
 * 摘要 prompt 模板。全站只需一种摘要形态，固定输出 Markdown 结构化详细摘要。
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static final String SYSTEM_PROMPT = """
            你是视频内容摘要助手。只依据给定字幕工作：不编造、不补充字幕之外的信息，用简体中文输出。""";

    public static final String USER_PROMPT = """
            请为这段视频写一份 Markdown 结构化详细摘要。
            注意：字幕可能来自自动识别（缺标点、有错别字、夹口头语与重复），请先按语义理解，不要把口头语和重复句写进去。
            1. 开头一段概括主题与核心论点（不要「本视频讲述了」这类套话），分段落展开主要内容（标题 + 分段），分段标题用「一、二、三…」编号；
            2. 按内容类型调整侧重：教程重步骤与结论、访谈重各方观点与分歧、新闻重事实与影响、娱乐重梗概与看点；
            3. 只写字幕里出现过的数字、人名、机构名与时间，不编造、不补充字幕之外的背景；
            4. 只用 Markdown 的标题、列表与加粗，不用表格、代码块与 HTML；直接输出正文，不要开场白与结束语。
            """;

    public static String buildUserMessage(String transcript) {
        return """
                视频字幕转录文本如下：

                ---
                %s
                ---

                %s
                """.formatted(transcript, USER_PROMPT);
    }
}
