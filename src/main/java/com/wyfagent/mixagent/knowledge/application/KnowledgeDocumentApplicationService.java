package com.wyfagent.mixagent.knowledge.application;

import com.wyfagent.mixagent.knowledge.application.command.UploadKnowledgeDocumentCommand;
import com.wyfagent.mixagent.knowledge.application.exception.DuplicateKnowledgeDocumentException;
import com.wyfagent.mixagent.knowledge.application.exception.InvalidKnowledgeDocumentException;
import com.wyfagent.mixagent.knowledge.application.exception.KnowledgeDocumentNotFoundException;
import com.wyfagent.mixagent.knowledge.application.exception.KnowledgeOperationConflictException;
import com.wyfagent.mixagent.knowledge.application.model.KnowledgeProcessingPolicy;
import com.wyfagent.mixagent.knowledge.application.result.UploadKnowledgeDocumentResult;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentSnapshot;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeDocumentStatus;
import com.wyfagent.mixagent.knowledge.domain.model.KnowledgeSourceType;
import com.wyfagent.mixagent.knowledge.domain.model.NewKnowledgeDocument;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeDocumentRepository;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeDocumentStorage;
import com.wyfagent.mixagent.knowledge.domain.port.KnowledgeProcessingScheduler;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * 知识文档管理用例。文件先落到受控存储，再写数据库；数据库写入失败时删除本次文件，
 * 避免出现数据库无记录但磁盘长期残留的孤儿文件。
 */
public class KnowledgeDocumentApplicationService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "md", "markdown");

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentStorage documentStorage;
    private final KnowledgeProcessingScheduler processingScheduler;
    private final KnowledgeProcessingPolicy policy;

    public KnowledgeDocumentApplicationService(
            KnowledgeDocumentRepository documentRepository,
            KnowledgeDocumentStorage documentStorage,
            KnowledgeProcessingScheduler processingScheduler,
            KnowledgeProcessingPolicy policy
    ) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.processingScheduler = processingScheduler;
        this.policy = policy;
    }

    public UploadKnowledgeDocumentResult upload(UploadKnowledgeDocumentCommand command) {
        String filename = sanitizeFilename(command.originalFilename());
        String extension = extractAllowedExtension(filename);
        byte[] content = command.content();
        validateFile(content);

        String contentHash = sha256(content);
        documentRepository.findByContentHash(contentHash).ifPresent(existing -> {
            throw new DuplicateKnowledgeDocumentException(existing.id());
        });

        String storageKey = documentStorage.store(contentHash, extension, content);
        long documentId;
        try {
            documentId = documentRepository.create(new NewKnowledgeDocument(
                    normalizeTitle(command.title(), filename),
                    filename,
                    normalizeMediaType(command.mediaType(), extension),
                    storageKey,
                    contentHash,
                    content.length,
                    KnowledgeSourceType.UPLOAD
            ));
        } catch (RuntimeException exception) {
            var existingDocument = documentRepository.findByContentHash(contentHash);
            if (existingDocument.isPresent()) {
                // 并发上传相同内容时两个请求共享同一个散列路径，失败方不能删除成功方的原文件。
                var existing = existingDocument.get();
                throw new DuplicateKnowledgeDocumentException(existing.id());
            }
            documentStorage.delete(storageKey);
            throw exception;
        }
        // 数据库登记已经成功后不再删除原文件；即使调度瞬时失败，启动恢复也能补投 UPLOADED 任务。
        processingScheduler.schedule(documentId);
        return new UploadKnowledgeDocumentResult(documentId, KnowledgeDocumentStatus.UPLOADED, contentHash);
    }

    public KnowledgeDocumentSnapshot get(long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new KnowledgeDocumentNotFoundException(documentId));
    }

    public KnowledgeDocumentSnapshot retry(long documentId) {
        KnowledgeDocumentSnapshot document = get(documentId);
        if (document.status() != KnowledgeDocumentStatus.FAILED) {
            throw new KnowledgeOperationConflictException("只有处理失败的知识文档可以重试");
        }
        if (!documentRepository.resetFailedForRetry(documentId)) {
            throw new KnowledgeOperationConflictException("文档状态已经变化，请刷新后重试");
        }
        processingScheduler.schedule(documentId);
        return get(documentId);
    }

    private void validateFile(byte[] content) {
        if (content.length == 0) {
            throw new InvalidKnowledgeDocumentException("知识文档不能为空");
        }
        if (content.length > policy.maxFileSizeBytes()) {
            throw new InvalidKnowledgeDocumentException("知识文档大小不能超过 " + policy.maxFileSizeBytes() + " 字节");
        }
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidKnowledgeDocumentException("文件名不能为空");
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (filename.isBlank() || filename.length() > 255 || filename.indexOf('\u0000') >= 0) {
            throw new InvalidKnowledgeDocumentException("文件名不合法或超过 255 个字符");
        }
        return filename;
    }

    private String extractAllowedExtension(String filename) {
        int separator = filename.lastIndexOf('.');
        String extension = separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidKnowledgeDocumentException("当前只支持 TXT 和 Markdown 文档");
        }
        return extension;
    }

    private String normalizeTitle(String requestedTitle, String filename) {
        String title = requestedTitle == null ? "" : requestedTitle.trim();
        if (title.isBlank()) {
            int extensionSeparator = filename.lastIndexOf('.');
            title = extensionSeparator > 0 ? filename.substring(0, extensionSeparator) : filename;
        }
        if (title.length() > 200) {
            throw new InvalidKnowledgeDocumentException("文档标题不能超过 200 个字符");
        }
        return title;
    }

    private String normalizeMediaType(String mediaType, String extension) {
        if (mediaType != null && !mediaType.isBlank() && mediaType.length() <= 100) {
            return mediaType.trim().toLowerCase(Locale.ROOT);
        }
        return "txt".equals(extension) ? "text/plain" : "text/markdown";
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 是 Java 17 必须提供的算法；若运行环境缺失，属于不可恢复的基础环境错误。
            throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256", exception);
        }
    }
}
