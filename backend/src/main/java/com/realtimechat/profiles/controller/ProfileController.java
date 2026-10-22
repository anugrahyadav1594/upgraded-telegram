package com.realtimechat.profiles.controller;

import com.realtimechat.common.pagination.PageResponse;
import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.config.AppProperties;
import com.realtimechat.profiles.dto.PublicProfileResponse;
import com.realtimechat.profiles.service.ProfileSearchService;
import com.realtimechat.profiles.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public profile and discovery endpoints.
 *
 * <p>Thin by design: validation of shape at the boundary, all rules inside the services. The
 * authenticated user is injected by {@code CurrentUserArgumentResolver} (never read from the request
 * body), so a client cannot ask "show me someone else's view of this profile".
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users & profiles", description = "Discovery, public profiles, self-service profile changes")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileSearchService profileSearchService;
    private final AppProperties properties;

    public ProfileController(ProfileService profileService,
                             ProfileSearchService profileSearchService,
                             AppProperties properties) {
        this.profileService = profileService;
        this.profileSearchService = profileSearchService;
        this.properties = properties;
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by username or display name (privacy and block filtered)")
    public ResponseEntity<PageResponse<PublicProfileResponse>> search(
            @RequestParam(name = "q") @Size(min = 2, max = 32, message = "Query must be 2-32 characters") String query,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor,
            AuthenticatedUser currentUser) {
        int pageSize = properties.getPagination().resolveLimit(limit);
        return ResponseEntity.ok(profileSearchService.search(currentUser.userId(), query, cursor, pageSize));
    }

    @GetMapping("/{username}")
    @Operation(summary = "Public profile by username (404 when hidden by privacy or a block)")
    public ResponseEntity<PublicProfileResponse> byUsername(@PathVariable("username") String username,
                                                           AuthenticatedUser currentUser) {
        return ResponseEntity.ok(profileService.getByUsername(username, currentUser.userId()));
    }
}
