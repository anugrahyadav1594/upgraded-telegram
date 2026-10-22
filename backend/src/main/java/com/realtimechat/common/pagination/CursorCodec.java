package com.realtimechat.common.pagination;

import com.realtimechat.common.error.InvalidRequestException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes and decodes opaque, tamper-evident pagination cursors.
 *
 * <p>Wire format: {@code base64url(base64url(key) + "." + uuid + "." + hmac)}. The HMAC
 * (keyed with {@code app.pagination.cursor-secret}, defaulting to the session secret) means a client
 * cannot craft a cursor that jumps to an arbitrary position, and following a cursor can never widen
 * the caller's authorization scope — the scope is always applied server-side alongside the cursor.
 *
 * <p>Malformed or mis-signed cursors are a client error ({@code 400 INVALID_REQUEST}), never a 500.
 */
@Component
public class CursorCodec {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = ".";
    private static final int MAX_CURSOR_LENGTH = 512;

    private final byte[] secret;
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    public CursorCodec(CursorSecretProvider provider) {
        this.secret = provider.secret().getBytes(StandardCharsets.UTF_8);
    }

    public String encode(Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        String key = encoder.encodeToString(cursor.sortKey().getBytes(StandardCharsets.UTF_8));
        String payload = key + SEPARATOR + cursor.id();
        return encoder.encodeToString(
                (payload + SEPARATOR + sign(payload)).getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        if (encoded.length() > MAX_CURSOR_LENGTH) {
            throw invalid();
        }
        try {
            String decoded = new String(decoder.decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\" + SEPARATOR);
            if (parts.length != 3) {
                throw invalid();
            }
            String payload = parts[0] + SEPARATOR + parts[1];
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw invalid();
            }
            String key = new String(decoder.decode(parts[0]), StandardCharsets.UTF_8);
            return Cursor.ofString(key, UUID.fromString(parts[1]));
        } catch (InvalidRequestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalid();
        }
    }

    private InvalidRequestException invalid() {
        return new InvalidRequestException("The pagination cursor is invalid");
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return encoder.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to sign pagination cursor", ex);
        }
    }
}
