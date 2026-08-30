package com.jobtracker.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A stable, documented page shape.
 *
 * <p>Spring's own Page serializes to a sprawling, unstable JSON blob that leaks
 * internals - it even warns about this on startup. Map to your own record instead.
 */
public record PageResponse<T>(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<T> content,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int page,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int size,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean first,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean last
) {
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
