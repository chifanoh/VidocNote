package com.vidoc.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.vidoc.dto.AgentDtos;
import com.vidoc.service.AgentChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summaries/{summaryId}/agent")
public class AgentChatController {

    private final AgentChatService agentChatService;

    public AgentChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    /**
     * 智能问答服务线程
     * @param summaryId
     * @param request
     * @return
     */
    @PostMapping("/chat")
    public AgentDtos.ChatResponse chat(@PathVariable String summaryId,
                                       @Valid @RequestBody AgentDtos.ChatRequest request) {
        return agentChatService.ask(StpUtil.getLoginIdAsLong(), summaryId, request.question());
    }
}
