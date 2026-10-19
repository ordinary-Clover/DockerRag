package org.example.service;

import org.example.util.DocParseUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
        String content = docParseUtil.parseFile(file);
        // SpringAI官方token分割器，替代简单字符串切割
        TokenTextSplitter splitter = new TokenTextSplitter(500, 100, 5, 10000, true);
        List<Document> docs = splitter.apply(List.of(new Document(content)));
        vectorStore.add(docs);
    }

    private List<Document> splitText(String text){
        // 简易分割：按固定长度切割，SpringAI自带TokenTextSplitter生产使用
        int chunkSize = 500;
        List<Document> documentList = new java.util.ArrayList<>();
        for(int i=0;i<text.length();i+=chunkSize){
            int end = Math.min(i+chunkSize, text.length());
            String chunk = text.substring(i,end);
            documentList.add(new Document(chunk));
        }
        return documentList;
    }
}