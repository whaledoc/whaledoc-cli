package io.whaledoc.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentExecutableTest {

    @Test
    void shouldDetectHomebrewWhenInstalledOnAppleSilicon() {

        // given
        Path executable = Path.of("/opt/homebrew/Cellar/whaledoc/0.1.1/bin/whaledoc");

        // when
        boolean actual = CurrentExecutable.isManagedByHomebrew(executable);

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void shouldDetectHomebrewWhenInstalledOnIntelMac() {

        // given
        Path executable = Path.of("/usr/local/Cellar/whaledoc/0.1.1/bin/whaledoc");

        // when
        boolean actual = CurrentExecutable.isManagedByHomebrew(executable);

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void shouldDetectHomebrewWhenInstalledOnLinux() {

        // given
        Path executable = Path.of("/home/linuxbrew/.linuxbrew/Cellar/whaledoc/0.1.1/bin/whaledoc");

        // when
        boolean actual = CurrentExecutable.isManagedByHomebrew(executable);

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void shouldNotDetectHomebrewWhenInstalledWithInstallScript() {

        // given
        Path executable = Path.of("/Users/jane/.whaledoc/bin/whaledoc");

        // when
        boolean actual = CurrentExecutable.isManagedByHomebrew(executable);

        // then
        assertThat(actual).isFalse();
    }
}
