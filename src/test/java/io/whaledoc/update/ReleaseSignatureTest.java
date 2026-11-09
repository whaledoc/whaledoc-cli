package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ReleaseSignatureTest {

    private static final String ALGORITHM = "Ed25519";
    private static final byte[] CHECKSUMS = "2cf24dba  whaledoc-linux-x64.tar.gz\n".getBytes(StandardCharsets.UTF_8);
    private static final String NOT_SIGNED = "The release isn't signed by WhaleDoc. The update was stopped and nothing was changed.";

    // The key check is signed with the release key's private half: proves the built-in key is the one releases use
    @Test
    void shouldPassKeyCheckWhenKeyIsReleaseKey() {

        // given
        ReleaseSignature releaseSignature = ReleaseSignature.official();

        // when
        Throwable thrown = catchThrowable(releaseSignature::checkKey);

        // then
        assertThat(thrown).isNull();
    }

    @Test
    void shouldFailKeyCheckWhenKeyIsAnotherKey() throws GeneralSecurityException {

        // given
        ReleaseSignature releaseSignature = new ReleaseSignature(generateKeyPair().getPublic().getEncoded());

        // when
        Throwable thrown = catchThrowable(releaseSignature::checkKey);

        // then
        assertThat(thrown).isInstanceOf(UpdateException.class).hasMessage(NOT_SIGNED);
    }

    @Test
    void shouldCarryTheKeyPublishedWithTheRepository() throws IOException {

        // given
        String published = Files.readString(Path.of("packaging/release-key.pub"));

        // when
        String body = published.lines().filter(line -> !line.startsWith("-----")).collect(Collectors.joining());

        // then
        assertThat(body).isEqualTo(ReleaseSignature.RELEASE_PUBLIC_KEY);
    }

    @Test
    void shouldRefuseContentWhenChangedAfterSigning() throws GeneralSecurityException {

        // given
        KeyPair keyPair = generateKeyPair();
        ReleaseSignature releaseSignature = new ReleaseSignature(keyPair.getPublic().getEncoded());
        byte[] signature = sign(CHECKSUMS, keyPair);
        byte[] changed = "0000000  whaledoc-linux-x64.tar.gz\n".getBytes(StandardCharsets.UTF_8);

        // when
        Throwable thrown = catchThrowable(() -> releaseSignature.verify(changed, signature));

        // then
        assertThat(thrown).isInstanceOf(UpdateException.class).hasMessage(NOT_SIGNED);
    }

    @Test
    void shouldRefuseContentWhenSignedWithAnotherKey() throws GeneralSecurityException {

        // given
        ReleaseSignature releaseSignature = ReleaseSignature.official();
        byte[] signature = sign(CHECKSUMS, generateKeyPair());

        // when
        Throwable thrown = catchThrowable(() -> releaseSignature.verify(CHECKSUMS, signature));

        // then
        assertThat(thrown).isInstanceOf(UpdateException.class).hasMessage(NOT_SIGNED);
    }

    @Test
    void shouldRefuseContentWhenSignatureIsMalformed() {

        // given
        ReleaseSignature releaseSignature = ReleaseSignature.official();
        byte[] signature = "not a signature".getBytes(StandardCharsets.UTF_8);

        // when
        Throwable thrown = catchThrowable(() -> releaseSignature.verify(CHECKSUMS, signature));

        // then
        assertThat(thrown).isInstanceOf(UpdateException.class).hasMessage(NOT_SIGNED);
    }

    static KeyPair generateKeyPair() throws GeneralSecurityException {

        return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
    }

    static byte[] sign(byte[] content, KeyPair keyPair) throws GeneralSecurityException {

        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(keyPair.getPrivate());
        signer.update(content);
        return signer.sign();
    }
}
