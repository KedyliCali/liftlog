package com.behlul.liftlog.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable JSON shape for paged lists. Serializing Spring's {@code Page} directly leaks internal
 * fields that may change between versions, which would break the Android client.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
