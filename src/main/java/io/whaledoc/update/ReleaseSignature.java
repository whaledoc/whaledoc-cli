package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Checks that a release's {@code checksums.txt} was signed with the WhaleDoc release key (Ed25519). The public key is
 * built into the CLI, so a release can't bring a key of its own. The key is read only when a signature is checked, so
 * no other command depends on it.
 */
public final class ReleaseSignature {

    private static final String ALGORITHM = "Ed25519";

    // The public half of the release key (packaging/release-key.pub without its PEM lines): X.509 SubjectPublicKeyInfo
    static final String RELEASE_PUBLIC_KEY = "MCowBQYDK2VwAyEAKIzkucP2Mq+RcMAL7f687X+8wyB1GA3TWnFzQ2um324=";

    // Signed once with the release key's private half: the built-in key must accept it
    private static final byte[] KEY_CHECK = "WhaleDoc release key check\n".getBytes(StandardCharsets.UTF_8);
    private static final String KEY_CHECK_SIGNATURE =
            "rlD4kueM2vgGC6gca6ISnWvwsqfOa7ZM7/A5e2+hpC9TTU2i9W7Rw73GwLxEY62C8QoMYRVFb03EsJ2qmzLJDA==";

    private final byte[] encodedKey;

    /** A signature check with the public key in X.509 SubjectPublicKeyInfo form, as {@code PublicKey.getEncoded()}. */
    public ReleaseSignature(byte[] encodedKey) {

        this.encodedKey = encodedKey.clone();
    }

    public static ReleaseSignature official() {

        return new ReleaseSignature(Base64.getDecoder().decode(RELEASE_PUBLIC_KEY));
    }

    public void verify(byte[] content, byte[] signature) {

        if (!isSignedByReleaseKey(content, signature)) {
            throw new UpdateException("The release isn't signed by WhaleDoc. The update was stopped and nothing was changed.");
        }
    }

    /** Checks a message signed with the release key, to prove this executable can check release signatures at all. */
    public void checkKey() {

        verify(KEY_CHECK, Base64.getDecoder().decode(KEY_CHECK_SIGNATURE));
    }

    private boolean isSignedByReleaseKey(byte[] content, byte[] signature) {

        Signature verifier = newVerifier();

        try {
            verifier.update(content);
            return verifier.verify(signature);

        } catch (SignatureException e) {
            // A signature of the wrong length or shape is as untrusted as a wrong one
            return false;
        }
    }

    private Signature newVerifier() {

        try {
            PublicKey publicKey = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(encodedKey));
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            return verifier;

        } catch (GeneralSecurityException e) {
            throw new UpdateException("Unable to check the release's signature.", e);
        }
    }
}
