package com.wyfagent.mixagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MixAgentApplicationTests {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.1-pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("mixagent_test")
            .withUsername("mixagent_test")
            .withPassword("mixagent_test");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesRequiredExtensionsAndTables() {
        // 使用真实 PostgreSQL + pgvector 验证迁移，避免 H2 无法覆盖扩展、JSONB 和向量字段的问题。
        Long extensionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname IN ('vector', 'pg_trgm')",
                Long.class
        );
        assertEquals(2L, extensionCount);

        Long tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('ingredient', 'recipe', 'recipe_ingredient',
                                     'knowledge_document', 'knowledge_chunk')
                """,
                Long.class
        );
        assertEquals(5L, tableCount);

        String embeddingType = jdbcTemplate.queryForObject(
                """
                SELECT udt_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'knowledge_chunk'
                  AND column_name = 'embedding'
                """,
                String.class
        );
        assertEquals("vector", embeddingType);

        String embeddingDefinition = jdbcTemplate.queryForObject(
                """
                SELECT format_type(attribute.atttypid, attribute.atttypmod)
                FROM pg_attribute attribute
                JOIN pg_class relation ON relation.oid = attribute.attrelid
                WHERE relation.relname = 'knowledge_chunk'
                  AND attribute.attname = 'embedding'
                  AND attribute.attnum > 0
                """,
                String.class
        );
        assertEquals("vector(1024)", embeddingDefinition);

        Long hnswIndexCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'knowledge_chunk'
                  AND indexname = 'idx_knowledge_chunk_embedding_hnsw'
                """,
                Long.class
        );
        assertEquals(1L, hnswIndexCount);
    }

}
