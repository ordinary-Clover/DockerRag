package org.example.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @description
 * @author qy
 * @Date 2026/10/14  13:07
 */
@Service
public class ChatService {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatService(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    public String chat(String question){
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(10)
                .similarityThreshold(0.5d)
                .build();
        var similarDocs = vectorStore.similaritySearch(request);
        
        if(similarDocs.isEmpty()){
            return "知识库未找到相关信息，请尝试换个问题或上传相关文档。";
        }
        
        StringBuilder context = new StringBuilder();
        for(int i = 0; i < similarDocs.size(); i++){
            context.append("[文档").append(i+1).append("] ")
                   .append(similarDocs.get(i).getText())
                   .append("\n\n");
        }

        String promptStr = """
                你是一个专业的本地知识库助手。请严格遵循以下规则回答用户问题：
                
                1. 只能基于【参考文档】中的内容回答，禁止编造或使用外部知识
                2. 如果参考文档中没有相关信息，直接回复："知识库未找到相关信息，请尝试换个问题或上传相关文档。"
                3. 回答要简洁准确，直接回答问题核心
                4. 如果可能，在回答末尾注明参考的文档编号，例如：[参考文档1]
                
                【参考文档】：
                {context}
                
                用户问题：{question}
                
                请回答：
                """;
        PromptTemplate promptTemplate = new PromptTemplate(promptStr);
        var prompt = promptTemplate.create(Map.of("context",context.toString(),"question",question));
        return chatClient.prompt(prompt).call().content();
    }

    public Flux<String> chatStream(String question){
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(15)
                .similarityThreshold(0.0d)
                .build();
        var similarDocs = vectorStore.similaritySearch(request);
        
        if(similarDocs.isEmpty()){
            return Flux.just("知识库未找到相关信息，请尝试换个问题或上传相关文档。");
        }
        
        List<Document> filteredDocs = similarDocs.stream()
                .filter(d -> d.getScore() != null)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .collect(Collectors.toList());
        
        if(filteredDocs.isEmpty()){
            return Flux.just("知识库未找到相关信息，请尝试换个问题或上传相关文档。");
        }
        
        double maxScore = filteredDocs.get(0).getScore();
        double threshold = Math.max(0.3, maxScore * 0.6);
        
        List<Document> topDocs = filteredDocs.stream()
                .filter(d -> d.getScore() >= threshold)
                .limit(5)
                .collect(Collectors.toList());
        
        if(topDocs.isEmpty() || topDocs.get(0).getScore() < 0.2){
            return Flux.just("知识库中未找到与问题直接相关的信息，请尝试换个问题或上传相关文档。");
        }
        
        StringBuilder context = new StringBuilder();
        for(int i = 0; i < topDocs.size(); i++){
            String text = topDocs.get(i).getText();
            if(text.length() > 2000){
                text = text.substring(0, 2000) + "...";
            }
            context.append("【参考资料").append(i+1).append("】\n").append(text).append("\n\n");
        }

        String promptStr = """
                你是一个严谨的知识库问答助手。请严格按照以下步骤回答问题：
                
                ## 思考步骤
                1. 理解用户问题的核心意图
                2. 在【参考资料】中查找与问题直接相关的内容
                3. 如果资料中没有相关信息，直接回复"知识库中未找到相关信息"
                4. 如果有相关信息，提取关键事实并组织回答
                
                ## 回答要求
                - **准确性优先**：只使用资料中的信息，禁止编造或推测
                - **直接回答**：先给出明确答案，再补充必要细节
                - **引用原文**：涉及数据、定义、步骤时，与资料保持一致
                - **标注来源**：回答末尾注明 [来源：参考资料X]
                - **简洁明了**：避免冗长解释和无关内容
                
                ## 参考资料
                {context}
                
                ## 用户问题
                {question}
                
                ## 回答
                """;
        PromptTemplate promptTemplate = new PromptTemplate(promptStr);
        var prompt = promptTemplate.create(Map.of("context",context.toString(),"question",question));
        return chatClient.prompt(prompt).stream().content();
    }
}