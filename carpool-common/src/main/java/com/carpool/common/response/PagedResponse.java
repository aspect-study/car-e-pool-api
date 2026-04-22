package com.carpool.common.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Consistent pagination wrapper for all paginated REST API responses.
 *
 * Structure:
 * {
 *   "success": true,
 *   "data": {
 *     "content": [...],
 *     "page": 0,
 *     "size": 10,
 *     "totalElements": 42,
 *     "totalPages": 5,
 *     "last": false
 *   }
 * }
 *
 * Usage:
 *   return ResponseEntity.ok(ApiResponse.ok(PagedResponse.of(page)));
 */
public record PagedResponse<T>(
        List<T>  content,
        int      page,
        int      size,
        long     totalElements,
        int      totalPages,
        boolean  last
) {
    /**
     * Build from Spring Data Page<T>.
     * Single conversion point — no scattered mapping logic in controllers.
     */
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}