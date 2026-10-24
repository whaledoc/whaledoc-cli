package io.whaledoc.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Signs forwarded events as <a href="https://www.standardwebhooks.com/">Standard Webhooks</a> specifies, so a local
 * endpoint verifies them with the same library and code as real deliveries: {@code v1,} and the Base64 of an
 * HMAC-SHA256 over {@code {webhook-id}.{webhook-timestamp}.{body}}, keyed with the secret after {@code whsec_}.
 */
public final class WebhookSigner {

    public static final String SECRET_PREFIX = "whsec_";

    private static final String SIGNATURE_VERSION = "v1,";
    private static final String HMAC = "HmacSHA256";
    private static final int SECRET_BYTES = 32;
    private static final int MASK_KEEPS = 4;
    private static final Pattern SECRET_FORMAT = Pattern.compile("whsec_[A-Za-z0-9+/]+={0,2}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebhookSigner() {
    }

    /** A new random secret in the Standard Webhooks format. */
    public static String newSecret() {

        byte[] key = new byte[SECRET_BYTES];
        RANDOM.nextBytes(key);
        return SECRET_PREFIX + Base64.getEncoder().encodeToString(key);
    }

    /** {@code whsec_…abcd}: enough to tell secrets apart, never the secret itself. */
    public static String masked(String secret) {

        return SECRET_PREFIX + "…" + secret.substring(secret.length() - MASK_KEEPS);
    }

    /** Whether {@code secret} is {@code whsec_} and Base64, as every Standard Webhooks secret is. */
    public static boolean isValidSecret(String secret) {

        if (!SECRET_FORMAT.matcher(secret).matches()) {
            return false;
        }

        try {
            return key(secret).length > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String signature(String secret, String webhookId, long timestamp, String body) {

        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key(secret), HMAC));
            // Concatenated, not formatted: %d would write the timestamp in the locale's digits
            String content = webhookId + "." + timestamp + "." + body;
            byte[] signed = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return SIGNATURE_VERSION + Base64.getEncoder().encodeToString(signed);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private static byte[] key(String secret) {

        return Base64.getDecoder().decode(secret.substring(SECRET_PREFIX.length()));
    }
}
