package com.realtimechat.auth.controller;

import com.realtimechat.auth.dto.AuthResponse;
import com.realtimechat.auth.dto.ChangePasswordRequest;
import com.realtimechat.auth.dto.ForgotPasswordRequest;
import com.realtimechat.auth.dto.LoginRequest;
import com.realtimechat.auth.dto.MessageResponse;
import com.realtimechat.auth.dto.RegisterRequest;
import com.realtimechat.auth.dto.ResetPasswordRequest;
import com.realtimechat.auth.dto.SessionResponse;
import com.realtimechat.auth.entity.UserSession;
import com.realtimechat.auth.security.SessionCookieFactory;
import com.realtimechat.auth.service.AuthService;
import com.realtimechat.auth.service.PasswordResetService;
import com.realtimechat.auth.service.SessionService;
import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.users.dto.AccountResponse;
import com.realtimechat.users.entity.User;
import com.realtimechat.users.service.AccountViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication endpoints.
 *
 * <p>The controller is thin on purpose — it converts HTTP to service calls and, where relevant,
 * writes cookies. All decisions (password policy, lockout, rate limits, token hashing) live in the
 * services so they cannot be bypassed by calling a different entry point.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, sessions and password recovery")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final SessionService sessionService;
    private final SessionCookieFactory cookieFactory;
    private final AccountViewService accountViewService;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService,
                          SessionService sessionService,
                          SessionCookieFactory cookieFactory,
                          AccountViewService accountViewService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.sessionService = sessionService;
        this.cookieFactory = cookieFactory;
        this.accountViewService = accountViewService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account and sign in")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        User user = authService.register(request.email(), request.password(), request.username(),
                request.displayName(), httpRequest);
        SessionService.IssuedSession session = sessionService.create(user, httpRequest);
        cookieFactory.writeSessionCookie(httpResponse, session.rawToken(),
                Duration.between(Instant.now(), session.expiresAt()));

        AccountResponse account = accountViewService.forUser(user);
        URI location = UriComponentsBuilder.fromPath("/api/v1/users/{username}")
                .buildAndExpand(account.username())
                .toUri();
        return ResponseEntity.created(location).body(AuthResponse.of(account, session.expiresAt()));
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email or username")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        User user = authService.login(request, httpRequest);
        // A brand-new token on every login: rotation limits how long a previously captured cookie
        // stays useful, and means "logged out" really is logged out.
        SessionService.IssuedSession session = sessionService.create(user, httpRequest);
        cookieFactory.writeSessionCookie(httpResponse, session.rawToken(),
                Duration.between(Instant.now(), session.expiresAt()));
        return ResponseEntity.ok(AuthResponse.of(accountViewService.forUser(user), session.expiresAt()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out of the current device")
    public ResponseEntity<Void> logout(AuthenticatedUser currentUser, HttpServletResponse httpResponse) {
        if (currentUser != null && currentUser.sessionId() != null) {
            sessionService.revoke(currentUser.userId(), currentUser.sessionId());
        }
        cookieFactory.clearSessionCookie(httpResponse);
        // Idempotent: calling logout twice, or with an already-expired session, must not be an error.
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Sign out of every device")
    public ResponseEntity<Void> logoutAll(AuthenticatedUser currentUser, HttpServletResponse httpResponse) {
        sessionService.revokeAll(currentUser.userId());
        cookieFactory.clearSessionCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "The authenticated account (the only endpoint exposing the caller's email)")
    public ResponseEntity<AccountResponse> me(AuthenticatedUser currentUser) {
        return ResponseEntity.ok(accountViewService.forUser(currentUser.userId()));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link (always 202, never reveals existence)")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.accepted().body(passwordResetService.requestReset(request.email()));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete a password reset with the emailed token")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the password (requires the current one; revokes other sessions)")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               AuthenticatedUser currentUser,
                                               HttpServletResponse httpResponse) {
        authService.changePassword(currentUser.userId(), currentUser.sessionId(),
                request.currentPassword(), request.newPassword());
        // The current session survives (rotate-in-place is handled by revokeOthers); the client is
        // told through `session.revoked` events on every other device.
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    @Operation(summary = "List active sessions of the caller")
    public ResponseEntity<Map<String, List<SessionResponse>>> sessions(AuthenticatedUser currentUser) {
        List<UserSession> sessions = sessionService.listActive(currentUser.userId());
        List<SessionResponse> items = sessions.stream()
                .map(session -> SessionResponse.from(session, currentUser.sessionId()))
                .toList();
        // Wrapped in the standard list envelope; sessions are not cursor-paginated (the count is
        // naturally small and bounded by the session limit per user).
        return ResponseEntity.ok(Map.of("items", items));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Revoke one session (403 when it is not the caller's)")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId, AuthenticatedUser currentUser) {
        boolean revoked = sessionService.revoke(currentUser.userId(), sessionId);
        if (!revoked) {
            // Distinguishing "not yours" from "not found" would leak the existence of session ids
            // belonging to other accounts, so both are reported as forbidden.
            throw new com.realtimechat.common.error.ForbiddenException("Session not found");
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
