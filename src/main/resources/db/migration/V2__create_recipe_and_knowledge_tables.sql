CREATE TABLE ingredient
(
    id                BIGSERIAL PRIMARY KEY,
    canonical_name    VARCHAR(100)  NOT NULL,
    normalized_name   VARCHAR(100)  NOT NULL,
    ingredient_type   VARCHAR(32)   NOT NULL,
    alcohol_by_volume NUMERIC(5, 2),
    description       TEXT,
    allergen_notes    TEXT,
    status             VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ingredient_normalized_name UNIQUE (normalized_name),
    CONSTRAINT ck_ingredient_abv CHECK (alcohol_by_volume IS NULL OR
                                        (alcohol_by_volume >= 0 AND alcohol_by_volume <= 100)),
    CONSTRAINT ck_ingredient_type CHECK (ingredient_type IN
                                         ('BASE_SPIRIT', 'LIQUEUR', 'WINE', 'BEER', 'MIXER', 'JUICE',
                                          'SYRUP', 'BITTER', 'GARNISH', 'OTHER')),
    CONSTRAINT ck_ingredient_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

COMMENT ON COLUMN ingredient.alcohol_by_volume IS
    '酒材本身的酒精体积分数，范围为 0-100；仅用于确定性计算，不允许由大模型猜测。';

CREATE INDEX idx_ingredient_name_trgm
    ON ingredient USING GIN (normalized_name gin_trgm_ops);

CREATE TABLE recipe
(
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(120) NOT NULL,
    normalized_name     VARCHAR(120) NOT NULL,
    description         TEXT,
    instructions        TEXT         NOT NULL,
    difficulty          VARCHAR(16)  NOT NULL DEFAULT 'BEGINNER',
    estimated_abv       NUMERIC(5, 2),
    serving_volume_ml   NUMERIC(8, 2),
    source_name         VARCHAR(200),
    source_reference    TEXT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_recipe_normalized_name UNIQUE (normalized_name),
    CONSTRAINT ck_recipe_difficulty CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT ck_recipe_abv CHECK (estimated_abv IS NULL OR (estimated_abv >= 0 AND estimated_abv <= 100)),
    CONSTRAINT ck_recipe_volume CHECK (serving_volume_ml IS NULL OR serving_volume_ml > 0),
    CONSTRAINT ck_recipe_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_recipe_name_trgm
    ON recipe USING GIN (normalized_name gin_trgm_ops);

CREATE TABLE recipe_ingredient
(
    id              BIGSERIAL      PRIMARY KEY,
    recipe_id       BIGINT         NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    ingredient_id   BIGINT         NOT NULL REFERENCES ingredient (id),
    amount_value    NUMERIC(10, 3) NOT NULL,
    unit            VARCHAR(16)    NOT NULL,
    preparation_note VARCHAR(200),
    optional        BOOLEAN        NOT NULL DEFAULT FALSE,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    CONSTRAINT ck_recipe_ingredient_amount CHECK (amount_value > 0),
    CONSTRAINT ck_recipe_ingredient_unit CHECK (unit IN ('ML', 'G', 'PIECE', 'DASH', 'DROP', 'TEASPOON')),
    CONSTRAINT ck_recipe_ingredient_sort_order CHECK (sort_order >= 0)
);

COMMENT ON COLUMN recipe_ingredient.amount_value IS
    '配方原始用量；液体进入酒精度和总体积计算前必须统一换算为毫升。';

CREATE INDEX idx_recipe_ingredient_ingredient_id
    ON recipe_ingredient (ingredient_id);

CREATE INDEX idx_recipe_ingredient_recipe_id
    ON recipe_ingredient (recipe_id, sort_order);

CREATE TABLE knowledge_document
(
    id                   BIGSERIAL PRIMARY KEY,
    title                VARCHAR(200) NOT NULL,
    original_filename    VARCHAR(255) NOT NULL,
    media_type           VARCHAR(100) NOT NULL,
    storage_key          VARCHAR(500) NOT NULL,
    content_hash         CHAR(64)     NOT NULL,
    source_type          VARCHAR(32)  NOT NULL,
    status               VARCHAR(24)  NOT NULL DEFAULT 'UPLOADED',
    chunk_strategy       VARCHAR(100),
    embedding_model      VARCHAR(200),
    embedding_dimensions INTEGER,
    version_no           INTEGER      NOT NULL DEFAULT 1,
    retry_count          INTEGER      NOT NULL DEFAULT 0,
    error_message        VARCHAR(1000),
    processed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_knowledge_document_hash UNIQUE (content_hash),
    CONSTRAINT ck_knowledge_document_status CHECK (status IN
                                                   ('UPLOADED', 'PARSING', 'CHUNKING', 'EMBEDDING',
                                                    'READY', 'FAILED', 'DISABLED')),
    CONSTRAINT ck_knowledge_document_source_type CHECK (source_type IN
                                                        ('UPLOAD', 'BUILT_IN', 'MANUAL', 'EXTERNAL')),
    CONSTRAINT ck_knowledge_document_dimensions CHECK (embedding_dimensions IS NULL OR
                                                       embedding_dimensions > 0),
    CONSTRAINT ck_knowledge_document_version CHECK (version_no > 0),
    CONSTRAINT ck_knowledge_document_retry CHECK (retry_count >= 0)
);

COMMENT ON COLUMN knowledge_document.storage_key IS
    '原始文件的受控存储位置；数据库只保存引用，不直接保存可能较大的文件二进制内容。';

CREATE INDEX idx_knowledge_document_status
    ON knowledge_document (status, created_at);

CREATE TABLE knowledge_chunk
(
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT      NOT NULL REFERENCES knowledge_document (id) ON DELETE CASCADE,
    chunk_index   INTEGER     NOT NULL,
    content       TEXT        NOT NULL,
    token_count   INTEGER,
    metadata      JSONB       NOT NULL DEFAULT '{}'::JSONB,
    embedding     VECTOR,
    search_vector TSVECTOR GENERATED ALWAYS AS
        (to_tsvector('simple', COALESCE(content, ''))) STORED,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_knowledge_chunk_order UNIQUE (document_id, chunk_index),
    CONSTRAINT ck_knowledge_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_knowledge_chunk_token_count CHECK (token_count IS NULL OR token_count > 0)
);

COMMENT ON COLUMN knowledge_chunk.embedding IS
    '向量维度由后续选定的向量模型决定；当前不创建 HNSW 索引，避免模型未确定时锁死维度。';

CREATE INDEX idx_knowledge_chunk_document_id
    ON knowledge_chunk (document_id);

CREATE INDEX idx_knowledge_chunk_search_vector
    ON knowledge_chunk USING GIN (search_vector);

CREATE INDEX idx_knowledge_chunk_metadata
    ON knowledge_chunk USING GIN (metadata);
