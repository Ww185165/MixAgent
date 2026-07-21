-- vector 用于保存与检索文本向量；pg_trgm 用于酒名、材料名和别名的模糊匹配。
-- 扩展初始化由 Flyway 统一管理，避免不同环境通过人工操作产生结构差异。
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
