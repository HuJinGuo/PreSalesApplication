package com.bidcollab.agent.tool;

import com.bidcollab.agent.runtime.ReActContext;
import com.bidcollab.agent.service.AgentDocumentOpsService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DocumentAgentTools {
    private final AgentDocumentOpsService opsService;

    public DocumentAgentTools(AgentDocumentOpsService opsService) {
        this.opsService = opsService;
    }

    @Tool("从知识库检索与当前文档任务相关的知识片段，并写入工作记忆")
    public String retrieve_knowledge(ReActContext context, Map<String, Object> args) {
        int hitCount = opsService.retrieveKnowledge(context, args).size();
        return "知识检索完成，命中 " + hitCount + " 条";
    }

    @Tool("根据需求和知识片段生成章节大纲")
    public String generate_outline(ReActContext context, Map<String, Object> args) {
        int rootNodes = opsService.generateOutline(context, args).size();
        return "大纲生成完成，一级节点 " + rootNodes + " 个";
    }

    @Tool("把大纲落库为章节树结构")
    public String persist_outline(ReActContext context, Map<String, Object> args) {
        int created = opsService.persistOutline(context, args);
        return "章节大纲落库完成，新增 " + created + " 个章节";
    }

    @Tool("逐章节生成正文并写入 section_version，自动记录引用 chunk")
    public String compose_sections(ReActContext context, Map<String, Object> args) {
        int count = opsService.composeSections(context, args);
        return "逐章节生成完成，写入 " + count + " 个章节";
    }

    @Tool("整篇生成草稿（章节化 JSON），暂不落库")
    public String compose_full_draft(ReActContext context, Map<String, Object> args) {
        int sectionCount = opsService.composeFullDraft(context, args);
        return "整篇草稿生成完成，包含 " + sectionCount + " 个章节";
    }

    @Tool("把整篇草稿按章节拆分并写入文档")
    public String split_persist_draft(ReActContext context, Map<String, Object> args) {
        int persisted = opsService.splitPersistDraft(context, args);
        return "草稿拆章落库完成，写入 " + persisted + " 个章节";
    }
}
