package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PlatformTest {

    @Test
    void shouldReturnMacosArm64WhenRunningOnAppleSilicon() {

        // given
        String osName = "Mac OS X";
        String osArch = "aarch64";

        // when
        Platform actual = Platform.from(osName, osArch);

        // then
        assertThat(actual).isEqualTo(Platform.MACOS_ARM64);
    }

    @Test
    void shouldReturnLinuxX64WhenRunningOnAmd64() {

        // given
        String osName = "Linux";
        String osArch = "amd64";

        // when
        Platform actual = Platform.from(osName, osArch);

        // then
        assertThat(actual).isEqualTo(Platform.LINUX_X64);
    }

    @Test
    void shouldReturnWindowsX64WhenRunningOnWindows() {

        // given
        String osName = "Windows 11";
        String osArch = "amd64";

        // when
        Platform actual = Platform.from(osName, osArch);

        // then
        assertThat(actual).isEqualTo(Platform.WINDOWS_X64);
    }

    @Test
    void shouldThrowUpdateExceptionWhenOperatingSystemIsUnsupported() {

        // given
        String osName = "FreeBSD";
        String osArch = "amd64";

        // when
        Throwable thrown = catchThrowable(() -> Platform.from(osName, osArch));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessage("Updates are not supported on FreeBSD (amd64).");
    }

    @Test
    void shouldReturnReleaseArchiveNameWhenPlatformIsWindows() {

        // given
        Platform platform = Platform.WINDOWS_X64;

        // when
        String actual = platform.archiveName();

        // then
        assertThat(actual).isEqualTo("whaledoc-windows-x64.zip");
    }
}
