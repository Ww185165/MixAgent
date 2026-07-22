package com.wyfagent.mixagent.knowledge.infrastructure.storage;

import com.wyfagent.mixagent.knowledge.application.exception.InvalidKnowledgeDocumentException;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeTextExtractor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** 第一版只提取纯文本，严格拒绝编码损坏的字节，避免静默产生不可检索乱码。 */
@Component
public class Utf8KnowledgeTextExtractor implements KnowledgeTextExtractor {

    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "text/plain", "text/markdown", "text/x-markdown",
            "application/octet-stream", "application/x-markdown");

    @Override
    public String extract(String originalFilename, String mediaType, byte[] content) {
        String normalizedMediaType = mediaType == null
                ? ""
                : mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!normalizedMediaType.isEmpty() && !ALLOWED_MEDIA_TYPES.contains(normalizedMediaType)) {
            throw new InvalidKnowledgeDocumentException("文件媒体类型与 TXT/Markdown 不匹配");
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            if (text.isBlank()) {
                throw new InvalidKnowledgeDocumentException("知识文档不包含有效文本");
            }
            return text;
        } catch (CharacterCodingException exception) {
            throw new InvalidKnowledgeDocumentException("知识文档必须使用 UTF-8 编码");
        }
    }
}
