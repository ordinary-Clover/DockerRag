package org.example.controller;

import org.example.service.ChatService;
import org.example.service.KnowledgeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

/**
 * @description
 * @author qy
 * @Date 2026/10/14  13:07
 */
@RestController
@RequestMapping("/api/kb")
@CrossOrigin(origins = "*")
public class KbController {
    private final KnowledgeService knowledgeService;
    private final ChatService chatService;

    public KbController(KnowledgeService knowledgeService, ChatService chatService) {
        this.knowledgeService = knowledgeService;
        this.chatService = chatService;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws Exception{
        knowledgeService.uploadDoc(file);
        return "文档上传、切片、向量化入库成功";
    }

    @PostMapping("/chat")
    public String chat(@RequestParam String question){
        return chatService.chat(question);
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String question){
        return chatService.chatStream(question);
    }
}