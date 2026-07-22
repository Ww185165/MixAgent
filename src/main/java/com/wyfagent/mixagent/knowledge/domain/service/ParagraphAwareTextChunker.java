package com.wyfagent.mixagent.knowledge.domain.service;

import com.wyfagent.mixagent.knowledge.domain.model.TextChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 优先在段落、换行或中文句末处分块，降低语义被硬切断的概率。
 * 当前使用字符窗口是为了避免依赖不匹配通义千问的 Tokenizer，参数会随评估结果调整。
 */
public class ParagraphAwareTextChunker {

    private static final List<String> BOUNDARIES = List.of("\n\n", "\n", "。", "！", "？", "；", ". ");

    public List<TextChunk> split(String text, int maxCharacters, int overlapCharacters) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int hardEnd = Math.min(start + maxCharacters, normalized.length());
            int end = hardEnd == normalized.length()
                    ? hardEnd
                    : findPreferredBoundary(normalized, start, hardEnd, maxCharacters / 2);

            String content = normalized.substring(start, end).trim();
            if (!content.isEmpty()) {
                chunks.add(new TextChunk(chunks.size(), content, content.length()));
            }

            if (end >= normalized.length()) {
                break;
            }
            int nextStart = Math.max(end - overlapCharacters, start + 1);
            start = skipLeadingWhitespace(normalized, nextStart);
        }
        return List.copyOf(chunks);
    }

    private int findPreferredBoundary(String text, int start, int hardEnd, int minimumOffset) {
        int minimumBoundary = start + minimumOffset;
        int best = -1;
        for (String boundary : BOUNDARIES) {
            int candidate = text.lastIndexOf(boundary, hardEnd - 1);
            if (candidate >= minimumBoundary) {
                best = Math.max(best, candidate + boundary.length());
            }
        }
        return best > start ? best : hardEnd;
    }

    private int skipLeadingWhitespace(String text, int position) {
        int current = position;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private String normalize(String text) {
        return text == null
                ? ""
                : text.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "").trim();
    }
}
