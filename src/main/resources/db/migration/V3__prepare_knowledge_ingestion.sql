ALTER TABLE knowledge_document
    ADD COLUMN file_size_bytes      BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN failure_code         VARCHAR(64),
    ADD COLUMN processing_started_at TIMESTAMPTZ,
    ADD COLUMN lock_version         INTEGER     NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_knowledge_document_file_size CHECK (file_size_bytes >= 0),
    ADD CONSTRAINT ck_knowledge_document_lock_version CHECK (lock_version >= 0);

ALTER TABLE knowledge_chunk
    ADD COLUMN character_count INTEGER,
    ADD CONSTRAINT ck_knowledge_chunk_character_count CHECK
        (character_count IS NULL OR character_count > 0);

-- text-embedding-v4 在当前实验中固定输出 1024 维。变更模型或维度时必须重建全部向量，
-- 不能把不同模型生成的向量放入同一索引后直接比较。
ALTER TABLE knowledge_chunk
    ALTER COLUMN embedding TYPE VECTOR(1024)
        USING embedding::VECTOR(1024);

CREATE INDEX idx_knowledge_chunk_embedding_hnsw
    ON knowledge_chunk USING HNSW (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

-- 中文连续文本的内置全文分词能力有限，三元组索引用于补充精确子串与模糊关键词召回。
CREATE INDEX idx_knowledge_chunk_content_trgm
    ON knowledge_chunk USING GIN (content gin_trgm_ops);
