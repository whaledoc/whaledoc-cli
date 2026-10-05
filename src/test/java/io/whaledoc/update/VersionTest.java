package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class VersionTest {

    @Test
    void shouldParseVersionWhenValueHasTagPrefix() {

        // given
        Version expectedVersion = new Version(1, 2, 3, null);

        // when
        Version actualVersion = Version.parse("v1.2.3");

        // then
        assertThat(actualVersion).usingRecursiveComparison().isEqualTo(expectedVersion);
    }

    @Test
    void shouldParsePreReleaseWhenValueHasLabelAndTrailingNewline() {

        // given
        Version expectedVersion = new Version(1, 3, 0, "beta.1");

        // when
        Version actualVersion = Version.parse("1.3.0-beta.1\n");

        // then
        assertThat(actualVersion).usingRecursiveComparison().isEqualTo(expectedVersion);
    }

    @Test
    void shouldThrowUpdateExceptionWhenValueIsNotAVersion() {

        // given
        String value = "<html>Not Found</html>";

        // when
        Throwable thrown = catchThrowable(() -> Version.parse(value));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessage("Invalid version: " + value);
    }

    @Test
    void shouldBeNewerWhenMinorVersionIsNumericallyHigher() {

        // given
        Version latest = Version.parse("1.10.0");
        Version current = Version.parse("1.9.5");

        // when
        boolean newer = latest.isNewerThan(current);

        // then
        assertThat(newer).isTrue();
    }

    @Test
    void shouldBeNewerWhenReleaseFollowsItsPreRelease() {

        // given
        Version release = Version.parse("1.2.0");
        Version preRelease = Version.parse("1.2.0-beta.1");

        // when
        boolean newer = release.isNewerThan(preRelease);

        // then
        assertThat(newer).isTrue();
    }

    @Test
    void shouldBeNewerWhenCurrentVersionIsDevelopmentBuild() {

        // given
        Version release = Version.parse("1.0.0");
        Version developmentBuild = Version.parse("0.0.0-SNAPSHOT");

        // when
        boolean newer = release.isNewerThan(developmentBuild);

        // then
        assertThat(newer).isTrue();
    }

    @Test
    void shouldNotBeNewerWhenVersionsAreEqual() {

        // given
        Version latest = Version.parse("1.2.3");
        Version current = Version.parse("v1.2.3");

        // when
        boolean newer = latest.isNewerThan(current);

        // then
        assertThat(newer).isFalse();
    }

    @Test
    void shouldFormatVersionWithPreReleaseWhenConvertedToString() {

        // given
        Version version = new Version(2, 0, 1, "rc.2");

        // when
        String actual = version.toString();

        // then
        assertThat(actual).isEqualTo("2.0.1-rc.2");
    }
}
