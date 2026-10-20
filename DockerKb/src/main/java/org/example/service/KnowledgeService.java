package org.example.service;

import org.example.util.DocParseUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @description
 * @author qy
 * @Date 2026/10/14  13:06
 */
@Service
public class KnowledgeService {
    private final DocParseUtil docParseUtil;
    private final VectorStore vectorStore;

    public KnowledgeService(DocParseUtil docParseUtil, VectorStore vectorStore) {
        this.docParseUtil = docParseUtil;
        this.vectorStore = vectorStore;
    }

    public void uploadDoc(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        
        // 1. 先删除该文件的旧数据（如果存在）
        if (filename != null && !filename.isEmpty()) {
            deleteByFilename(filename);
        }
        
        // 2. 解析并添加新数据
        String content = docParseUtil.parseFile(file);
        List<Document> docs = splitByLogicalLines(content, filename);
        vectorStore.add(docs);
    }

    /**
     * 根据文件名删除向量库中的旧数据
     */
    private void deleteByFilename(String filename) {
        try {
            // 使用文件名作为过滤条件查询并删除
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(filename)
                    .topK(1000)
                    .filterExpression(String.format("filename == '%s'", filename))
                    .build();
            
            List<Document> existingDocs = vectorStore.similaritySearch(searchRequest);
            if (!existingDocs.isEmpty()) {
                List<String> ids = existingDocs.stream()
                        .map(Document::getId)
                        .toList();
                vectorStore.delete(ids);
            }
        } catch (Exception e) {
            // 如果删除失败，记录日志但不中断上传流程
            System.err.println("删除旧数据失败: " + e.getMessage());
        }
    }

    /**
     * 按逻辑行切分：每个 chunk 最多 maxChars 个字符，但在行边界处断开。
     * 解析时每个表格行占一行（如 "[行N] 姓名：张三 | 部门：技术部 | 薪资：8000"），
     * 该行已内嵌列名、自包含语义，因此整行纳入同一 chunk 可避免信息被拦腰截断，
     * 从而提升复杂表格内容的检索召回与回答准确性。
     */
    private List<Document> splitByLogicalLines(String text, String filename) {
        int maxChars = 500;
        List<Document> docs = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();

        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 单行超长时按字符兜底拆分，避免无限累积
            if (trimmed.length() > maxChars) {
                flush(chunk, docs, filename);
                for (int i = 0; i < trimmed.length(); i += maxChars) {
                    Document doc = new Document(trimmed.substring(i, Math.min(i + maxChars, trimmed.length())));
                    doc.getMetadata().put("filename", filename);
                    docs.add(doc);
                }
                continue;
            }
            // 若加上当前行会超过上限，先落盘当前 chunk，保证行完整
            if (chunk.length() + trimmed.length() + 1 > maxChars && chunk.length() > 0) {
                flush(chunk, docs, filename);
            }
            if (chunk.length() > 0) {
                chunk.append("\n");
            }
            chunk.append(trimmed);
        }
        flush(chunk, docs, filename);
        return docs;
    }

    private void flush(StringBuilder chunk, List<Document> docs, String filename) {
        if (chunk.length() > 0) {
            Document doc = new Document(chunk.toString());
            doc.getMetadata().put("filename", filename);
            docs.add(doc);
            chunk.setLength(0);
        }
    }
}