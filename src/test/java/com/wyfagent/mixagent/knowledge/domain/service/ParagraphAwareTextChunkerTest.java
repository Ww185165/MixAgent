package com.wyfagent.mixagent.knowledge.domain.service;

import com.wyfagent.mixagent.knowledge.domain.model.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParagraphAwareTextChunkerTest {

    private final ParagraphAwareTextChunker chunker = new ParagraphAwareTextChunker();

    @Test
    void splitsAtReadableBoundaryAndKeepsConfiguredMaximum() {
        String text = "金酒带来杜松子香气。柠檬汁提供酸度。糖浆负责平衡。"
                + "苏打水增加气泡感。饮用前需要充分加冰。";

        List<TextChunk> chunks = chunker.split(text, 30, 5);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.characterCount() <= 30));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.characterCount() == chunk.content().length()));
        assertEquals(0, chunks.get(0).index());
        assertFalse(chunks.get(0).content().isBlank());
    }

    @Test
    void returnsNoChunksForBlankInput() {
        assertEquals(List.of(), chunker.split(" \r\n\u0000 ", 800, 100));
    }
}
