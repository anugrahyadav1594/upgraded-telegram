package com.realtimechat.users.controller;

import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.profiles.dto.MyProfileResponse;
import com.realtimechat.profiles.dto.UpdateProfileRequest;
import com.realtimechat.profiles.service.ProfileService;
import com.realtimechat.users.dto.AccountResponse;
import com.realtimechat.users.service.AccountDeletionService;
import com.realtimechat.users.service.AccountViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account endpoints.
 *
 * <p>These are the only endpoints that may return the caller's email address, and the identity always
 * comes from the session — there is no "get user X's account" endpoint at all, which is the simplest
 * possible defence against IDOR on private fields.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Account", description = "The authenticated user's own account and profile")
public class UserController {

    private final AccountViewService accountViewService;
    private final ProfileService profileService;
    private final AccountDeletionService accountDeletionService;

    public UserController(AccountViewService accountViewService,
                          ProfileService profileService,
                          AccountDeletionService accountDeletionService) {
        this.accountViewService = accountViewService;
        this.profileService = profileService;
        this.accountDeletionService = accountDeletionService;
    }

    @GetMapping
    @Operation(summary = "Current account, profile and settings")
    public ResponseEntity<AccountResponse> me(AuthenticatedUser currentUser) {
        return ResponseEntity.ok(accountViewService.forUser(currentUser.userId()));
    }

    @PatchMapping
    @Operation(summary = "Update the pseudonymous profile (display name, bio, avatar, username)")
    public ResponseEntity<MyProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                           AuthenticatedUser currentUser) {
        var summary = profileService.updateMyProfile(currentUser.userId(), request);
        var profile = profileService.findEntity(currentUser.userId()).orElseThrow();
        return ResponseEntity.ok(new MyProfileResponse(
                profile.getUserId(),
                profile.getUsername(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getAvatarUrl(),
                profile.isUsernamePublic(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()));
    }

    @DeleteMapping
    @Operation(summary = "Delete the account: erase personal data, anonymise authored messages")
    public ResponseEntity<Void> deleteAccount(AuthenticatedUser currentUser) {
        accountDeletionService.deleteAccount(currentUser.userId());
        return ResponseEntity.noContent().build();
    }
}
