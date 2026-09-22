package com.realtimechat.profiles.service;

import com.realtimechat.common.pagination.Cursor;
import com.realtimechat.common.pagination.CursorCodec;
import com.realtimechat.common.pagination.PageResponse;
import com.realtimechat.profiles.dto.PublicProfileResponse;
import com.realtimechat.profiles.entity.Profile;
import com.realtimechat.profiles.repository.ProfileRepository;
import com.realtimechat.users.service.BlockQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * User discovery: "find someone to talk to".
 *
 * <p>Design notes:
 * <ul>
 *   <li><b>Bounded and paginated.</b> Results are ordered by username with a signed cursor, so a
 *       deep page is as cheap as the first and {@code OFFSET} is never used.</li>
 *   <li><b>Privacy respected server-side.</b> Users with {@code show_profile_in_search = false} are
 *       filtered out in SQL (a join on privacy settings), so they are never loaded, let alone
 *       returned.</li>
 *   <li><b>Block-aware.</b> Block-related users are excluded in both directions, so search cannot be
 *       used to observe somebody who blocked you.</li>
 *   <li><b>No email enumeration.</b> The query only touches {@code profiles} and matches
 *       username/display name — an email address cannot be probed.</li>
 *   <li><b>Minimum query length of 2.</b> A one-character query would return a large slice of the
 *       user base and turn search into an enumeration endpoint.</li>
 * </ul>
 */
@Service
public class ProfileSearchService {

    private static final int MAX_RESULTS = 100;
    private static final int MIN_QUERY_LENGTH = 2;

    private final ProfileRepository profileRepository;
    private final BlockQueryService blockQueryService;
    private final CursorCodec cursorCodec;

    public ProfileSearchService(ProfileRepository profileRepository,
                                BlockQueryService blockQueryService,
                                CursorCodec cursorCodec) {
        this.profileRepository = profileRepository;
        this.blockQueryService = blockQueryService;
        this.cursorCodec = cursorCodec;
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicProfileResponse> search(UUID requesterId, String rawQuery, String rawCursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_RESULTS);
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < MIN_QUERY_LENGTH) {
            return PageResponse.empty(pageSize);
        }

        String escaped = escapeLike(query.toLowerCase(Locale.ROOT));
        String prefixPattern = escaped + "%";
        String containsPattern = "%" + escaped + "%";

        Set<UUID> excluded = blockQueryService.usersInvisibleTo(requesterId);
        Cursor cursor = cursorCodec.decode(rawCursor);

        // Over-fetch by one row: `hasMore` is then exact without a COUNT(*) over the user base.
        List<Profile> rows = profileRepository.search(
                requesterId,
                prefixPattern,
                containsPattern,
                excluded.isEmpty() ? new ArrayList<>(List.of(requesterId)) : new ArrayList<>(excluded),
                excluded.isEmpty(),
                cursor == null ? null : cursor.sortKey(),
                cursor == null ? null : cursor.id(),
                pageSize + 1);

        boolean hasMore = rows.size() > pageSize;
        List<Profile> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<PublicProfileResponse> items = pageRows.stream().map(this::toSearchResult).toList();

        String nextCursor = null;
        if (hasMore && !pageRows.isEmpty()) {
            Profile last = pageRows.get(pageRows.size() - 1);
            nextCursor = cursorCodec.encode(Cursor.ofString(last.getUsername(), last.getUserId()));
        }
        return new PageResponse<>(List.copyOf(items), nextCursor, hasMore, pageSize);
    }

    /**
     * Search results intentionally omit presence: enriching 25 rows would mean 25 presence lookups on
     * the hottest discovery path. Clients render the neutral row and receive presence for the
     * conversation or profile they actually open.
     */
    private PublicProfileResponse toSearchResult(Profile profile) {
        return new PublicProfileResponse(
                profile.getUserId(),
                profile.isUsernamePublic() ? profile.getUsername() : null,
                profile.isUsernamePublic(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getBio(),
                null,
                null,
                profile.getCreatedAt(),
                null,
                false,
                true);
    }

    /** Escapes LIKE metacharacters so a query of {@code "%"} cannot match every row. */
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
