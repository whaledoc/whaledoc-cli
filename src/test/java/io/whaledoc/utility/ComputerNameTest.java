package io.whaledoc.utility;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ComputerNameTest {

    @Test
    void shouldFindThisComputersNameWhenTheSystemHasOne() {

        // when
        Optional<String> name = ComputerName.current();

        // then
        assertThat(name).hasValueSatisfying(value -> assertThat(value).isNotBlank().doesNotEndWithIgnoringCase(".local"));
    }
}
