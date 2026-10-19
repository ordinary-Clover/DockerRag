package org.example.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @description Chroma向量库配置
 * @author qy
 * @Date 2026/10/14  14:55
 */
@Configuration
public class ChromaConfig {

    @Value("${spring.ai.vectorstore.chroma.collection-name:kb_collection}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.chroma.client.url}")
    private String chromaUrl;

    @Bean
    public ChromaApi chromaApi() {
        return new ChromaApi(chromaUrl);
    }

    @Bean
    public ChromaVectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(true)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}