package org.example.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
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

        // 问题语义路由 + 汇总优先
        List<Document> orderedDocs = resolveDocs(question, similarDocs, 0.0d);

        StringBuilder context = new StringBuilder();
        for(int i = 0; i < orderedDocs.size(); i++){
            context.append("[文档").append(i+1).append("] ")
                   .append(orderedDocs.get(i).getText())
                   .append("\n\n");
        }

        String promptStr = """
                你是一个专业的本地知识库助手。请严格遵循以下规则回答用户问题：

                1. 只能基于【参考文档】中的内容回答，禁止编造或使用外部知识
                2. **优先引用汇总条目**：若文档中存在标注 `[汇总行]` / `【表格汇总】` / `【合计】` 的条目，请直接、逐字引用该条目中的数值作为答案
                3. 如果参考文档中没有相关信息，直接回复："知识库未找到相关信息，请尝试换个问题或上传相关文档。"
                4. 回答要简洁准确，直接回答问题核心
                5. 如果可能，在回答末尾注明参考的文档编号，例如：[参考文档1]

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

        List<Document> topDocs = resolveDocs(question, filteredDocs, threshold);
        if (topDocs.isEmpty() || topDocs.get(0).getScore() < 0.2){
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
                - **优先引用汇总条目**：若参考资料中存在标注 `[汇总行]` / `【表格汇总】` / `【合计】` 的条目，
                  请直接、逐字引用该条目中的数值作为答案，并把该条目标注为来源。
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

    /**
     * 问题语义路由：
     * 1) 若问题在问"合计/总计/汇总"这类表格汇总值，则只保留含显式汇总标记（[汇总行] / 【表格汇总】 / 【合计】）
     *    的文档，彻底剔除明细数据行 —— 明细不在上下文，模型就无法把多个分项相加去推算。
     * 2) 否则按相似度阈值截断，并保证含汇总标记的文档排在最前。
     */
    private List<Document> resolveDocs(String question, List<Document> docs, double threshold) {
        boolean asksTotal = asksForAggregate(question);
        List<Document> totals = new ArrayList<>();
        List<Document> others = new ArrayList<>();
        for (Document d : docs) {
            String t = d.getText() == null ? "" : d.getText();
            if (isTotalDoc(t)) {
                totals.add(d);
            } else {
                others.add(d);
            }
        }

        if (asksTotal) {
            // 只用含汇总标记的文档；若召回里没有，退回全部候选（保持原有可用性）
            if (!totals.isEmpty()) {
                return totals;
            }
            return docs;
        }

        List<Document> result = new ArrayList<>(totals);
        for (Document d : others) {
            if (d.getScore() != null && d.getScore() >= threshold) {
                result.add(d);
            }
        }
        if (result.size() > 5) {
            result = new ArrayList<>(result.subList(0, 5));
        }
        if (result.isEmpty()) {
            result = docs.size() > 5 ? new ArrayList<>(docs.subList(0, 5)) : new ArrayList<>(docs);
        }
        return result;
    }

    /** 问题是否在询问汇总/合计类数值 */
    private boolean asksForAggregate(String question) {
        if (question == null) {
            return false;
        }
        String q = question;
        return q.contains("合计") || q.contains("总计") || q.contains("汇总")
                || q.contains("总数") || q.contains("总和") || q.contains("小计");
    }

    /** 文档是否含显式汇总标记 */
    private boolean isTotalDoc(String text) {
        return text.contains("[汇总行]") || text.contains("【表格汇总】") || text.contains("【合计】");
    }
}