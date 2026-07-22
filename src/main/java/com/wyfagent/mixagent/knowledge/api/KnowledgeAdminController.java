package com.wyfagent.mixagent.knowledge.api;

import com.wyfagent.mixagent.knowledge.api.dto.HybridSearchRequest;
import com.wyfagent.mixagent.knowledge.api.dto.KnowledgeDocumentResponse;
import com.wyfagent.mixagent.knowledge.api.dto.UploadKnowledgeDocumentResponse;
import com.wyfagent.mixagent.knowledge.application.HybridKnowledgeSearchService;
import com.wyfagent.mixagent.knowledge.application.KnowledgeDocumentApplicationService;
import com.wyfagent.mixagent.knowledge.application.command.UploadKnowledgeDocumentCommand;
import com.wyfagent.mixagent.knowledge.application.exception.InvalidKnowledgeDocumentException;
import com.wyfagent.mixagent.knowledge.application.result.UploadKnowledgeDocumentResult;
import com.wyfagent.mixagent.knowledge.domain.model.HybridKnowledgeSearchResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** 知识库后台接口，所有路径都由安全配置要求 KNOWLEDGE_ADMIN（知识管理员）角色。 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeDocumentApplicationService documentService;
    private final HybridKnowledgeSearchService searchService;

    public KnowledgeAdminController(
            KnowledgeDocumentApplicationService documentService,
            HybridKnowledgeSearchService searchService
    ) {
        this.documentService = documentService;
        this.searchService = searchService;
    }

    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadKnowledgeDocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title
    ) {
        try {
            UploadKnowledgeDocumentResult result = documentService.upload(new UploadKnowledgeDocumentCommand(
                    title, file.getOriginalFilename(), file.getContentType(), file.getBytes()));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(UploadKnowledgeDocumentResponse.from(result));
        } catch (IOException exception) {
            throw new InvalidKnowledgeDocumentException("上传文件无法读取");
        }
    }

    @GetMapping("/documents/{documentId}")
    public KnowledgeDocumentResponse get(@PathVariable @Positive long documentId) {
        return KnowledgeDocumentResponse.from(documentService.get(documentId));
    }

    @PostMapping("/documents/{documentId}/retry")
    public ResponseEntity<KnowledgeDocumentResponse> retry(@PathVariable @Positive long documentId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(KnowledgeDocumentResponse.from(documentService.retry(documentId)));
    }

    @PostMapping("/search")
    public List<HybridKnowledgeSearchResult> search(@Valid @RequestBody HybridSearchRequest request) {
        return searchService.search(request.query(), request.effectiveTopK());
    }
}
