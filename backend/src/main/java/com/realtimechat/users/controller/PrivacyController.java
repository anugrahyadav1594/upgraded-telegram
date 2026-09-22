package com.realtimechat.users.controller;

import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.users.dto.PrivacySettingsResponse;
import com.realtimechat.users.dto.UpdatePrivacyRequest;
import com.realtimechat.users.entity.UserPrivacySettings;
import com.realtimechat.users.service.PrivacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Privacy settings.
 *
 * <p>Changes take effect immediately and server-side: the next presence event, profile response and
 * search query already respects them. The UI never decides.
 */
@RestController
@RequestMapping("/api/v1/privacy")
@Tag(name = "Privacy", description = "Visibility and messaging preferences")
public class PrivacyController {

    private final PrivacyService privacyService;

    public PrivacyController(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    @GetMapping
    @Operation(summary = "Read the current privacy settings")
    public ResponseEntity<PrivacySettingsResponse> get(AuthenticatedUser currentUser) {
        return ResponseEntity.ok(PrivacySettingsResponse.from(privacyService.getOrDefault(currentUser.userId())));
    }

    @PatchMapping
    @Operation(summary = "Update privacy settings (partial; only provided fields change)")
    public ResponseEntity<PrivacySettingsResponse> update(@Valid @RequestBody UpdatePrivacyRequest request,
                                                          AuthenticatedUser currentUser) {
        UserPrivacySettings current = privacyService.getOrDefault(currentUser.userId());
        boolean showOnline = request.showOnlineStatus() == null
                ? current.isShowOnlineStatus() : request.showOnlineStatus();
        boolean showLastSeen = request.showLastSeen() == null
                ? current.isShowLastSeen() : request.showLastSeen();
        boolean searchable = request.showProfileInSearch() == null
                ? current.isShowProfileInSearch() : request.showProfileInSearch();
        UserPrivacySettings.MessagePolicy policy = request.allowMessagesFrom() == null
                ? current.getAllowMessagesFrom()
                : UserPrivacySettings.MessagePolicy.valueOf(request.allowMessagesFrom());
        boolean receipts = request.readReceipts() == null ? current.isReadReceipts() : request.readReceipts();

        return ResponseEntity.ok(PrivacySettingsResponse.from(privacyService.update(
                currentUser.userId(), showOnline, showLastSeen, searchable, policy, receipts)));
    }
}
