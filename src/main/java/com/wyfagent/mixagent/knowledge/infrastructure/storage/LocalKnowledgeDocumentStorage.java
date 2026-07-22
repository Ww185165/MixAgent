package com.wyfagent.mixagent.knowledge.infrastructure.storage;

import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeDocumentStorage;
import com.wyfagent.mixagent.knowledge.infrastructure.config.KnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 使用内容散列生成受控相对路径，调用方无法通过文件名实施目录穿越。 */
@Component
public class LocalKnowledgeDocumentStorage implements KnowledgeDocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalKnowledgeDocumentStorage.class);

    private final Path storageRoot;

    public LocalKnowledgeDocumentStorage(KnowledgeProperties properties) {
        if (properties.storageRoot() == null || properties.storageRoot().isBlank()) {
            throw new IllegalArgumentException("知识文档存储目录不能为空");
        }
        this.storageRoot = Path.of(properties.storageRoot()).toAbsolutePath().normalize();
    }

    @Override
    public String store(String contentHash, String extension, byte[] content) {
        validateHash(contentHash);
        String storageKey = contentHash.substring(0, 2) + "/" + contentHash + "." + extension;
        Path target = resolveControlledPath(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), contentHash + "-", ".uploading");
            try {
                Files.write(temporary, content);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return storageKey;
        } catch (IOException exception) {
            throw new KnowledgeStorageException("知识文档无法写入本地存储", exception);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolveControlledPath(storageKey));
        } catch (IOException exception) {
            throw new KnowledgeStorageException("知识文档原文件不存在或无法读取", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveControlledPath(storageKey));
        } catch (IOException exception) {
            // 删除只用于失败补偿，记录问题但不覆盖真正的数据库异常。
            log.warn("知识文档补偿删除失败，storageKey={}", storageKey, exception);
        }
    }

    private Path resolveControlledPath(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new KnowledgeStorageException("知识文档存储键为空");
        }
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new KnowledgeStorageException("知识文档存储键越过受控目录");
        }
        return resolved;
    }

    private void validateHash(String contentHash) {
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new KnowledgeStorageException("知识文档内容散列格式不正确");
        }
    }
}
