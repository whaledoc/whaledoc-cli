package io.whaledoc.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    /** The example in the Standard Webhooks specification, so signatures match every library that verifies them. */
    private static final String SPEC_SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
    private static final String SPEC_ID = "msg_p5jXN8AQM9LWM0D4loKWxJek";
    private static final long SPEC_TIMESTAMP = 1614265330;
    private static final String SPEC_BODY = "{\"test\": 2432232314}";
    private static final String SPEC_SIGNATURE = "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=";

    @Test
    void shouldMatchSpecificationExampleWhenSigning() {

        // given
        String expectedSignature = SPEC_SIGNATURE;

        // when
        String signature = WebhookSigner.signature(SPEC_SECRET, SPEC_ID, SPEC_TIMESTAMP, SPEC_BODY);

        // then
        assertThat(signature).isEqualTo(expectedSignature);
    }

    @Test
    void shouldKeepOnlyPrefixAndLastCharactersWhenMaskingSecret() {

        // given
        String secret = SPEC_SECRET;

        // when
        String masked = WebhookSigner.masked(secret);

        // then
        assertThat(masked).isEqualTo("whsec_…LaSw");
    }

    @Test
    void shouldMakeValidDistinctSecretsWhenCreatingNewOnes() {

        // given
        String first = WebhookSigner.newSecret();

        // when
        String second = WebhookSigner.newSecret();

        // then
        assertThat(WebhookSigner.isValidSecret(first)).isTrue();
        assertThat(second).isNotEqualTo(first).startsWith("whsec_");
    }

    @Test
    void shouldRejectSecretWhenItIsNotStandardWebhooksFormat() {

        // given
        String[] invalid = {"not-a-secret", "whsec_", "whsec_not base64!", "sk_live_abc"};

        // when
        boolean anyAccepted = java.util.Arrays.stream(invalid).anyMatch(WebhookSigner::isValidSecret);

        // then
        assertThat(anyAccepted).isFalse();
    }
}
