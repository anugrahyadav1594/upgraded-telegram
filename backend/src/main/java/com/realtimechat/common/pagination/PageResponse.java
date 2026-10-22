package com.realtimechat.common.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * Uniform paginated envelope: {@code { items, nextCursor, hasMore, limit }}.
 *
 * <p>Built from an over-fetched row list (limit + 1) so {@code hasMore} is exact without a
 * {@code COUNT(*)}, which on a large message table would cost more than the page itself.
 */
public record PageResponse<T>(List<T> items, String nextCursor, boolean hasMore, int limit) {

    public static <T> PageResponse<T> empty(int limit) {
        return new PageResponse<>(List.of(), null, false, limit);
    }

    /**
     * @param rows            up to {@code limit + 1} rows (the extra row only signals {@code hasMore})
     * @param cursorExtractor how to build the cursor of the last returned item
     */
    public static <T> PageResponse<T> of(List<T> rows, int limit,
                                        Function<T, Cursor> cursorExtractor,
                                        CursorCodec codec) {
        boolean hasMore = rows.size() > limit;
        List<T> page = hasMore ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            nextCursor = codec.encode(cursorExtractor.apply(page.get(page.size() - 1)));
        }
        return new PageResponse<>(page, nextCursor, hasMore, limit);
    }
}
