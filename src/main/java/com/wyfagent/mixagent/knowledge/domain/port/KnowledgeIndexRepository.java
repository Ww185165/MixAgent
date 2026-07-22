package com.wyfagent.mixagent.knowledge.domain.port;

import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeChunkData;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeSearchCandidate;

import java.util.List;

/**
 * 知识索引端口，封装片段替换以及关键词、向量两路召回。
 */
public interface KnowledgeIndexRepository {

    void replaceAndMarkReady(
            long documentId,
            List<KnowledgeChunkData> chunks,
            String chunkStrategy,
            String embeddingModel,
            int embeddingDimensions
    );

    List<KnowledgeSearchCandidate> vectorSearch(List<Float> queryVector, int limit);

    List<KnowledgeSearchCandidate> keywordSearch(String query, int limit);
}
