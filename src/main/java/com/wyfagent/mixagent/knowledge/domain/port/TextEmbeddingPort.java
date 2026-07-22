package com.wyfagent.mixagent.knowledge.domain.port;

import com.wyfagent.mixagent.knowledge.domain.model.EmbeddingBatch;

import java.util.List;

/**
 * 文本向量化端口，使应用层可使用假模型进行测试且不产生云端调用费用。
 */
public interface TextEmbeddingPort {

    EmbeddingBatch embedAll(List<String> texts);
}
