package com.wyfagent.mixagent.knowledge.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param topK 返回结果数，省略时默认为 5，硬上限防止无限检索。 */
public record HybridSearchRequest(
        @NotBlank(message = "检索文本不能为空")
        @Size(max = 1000, message = "检索文本不能超过 1000 个字符")
        String query,
        @Min(value = 1, message = "topK 不能小于 1")
        @Max(value = 20, message = "topK 不能大于 20")
        Integer topK
) {
    public int effectiveTopK() {
        return topK == null ? 5 : topK;
    }
}
