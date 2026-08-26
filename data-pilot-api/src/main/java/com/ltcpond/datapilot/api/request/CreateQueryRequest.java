package com.ltcpond.datapilot.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 自然语言问数请求。 */
public record CreateQueryRequest(
        @NotBlank @Size(max = 1000) String question,
        @Min(1) @Max(200) Integer maxRows) {
}
