package io.whaledoc.console;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextTableTest {

    private static final List<String> HEADERS = List.of("", "NAME", "SLUG");
    private static final List<List<String>> ROWS = List.of(
            List.of("*", "Acme HR", "acme-hr"),
            List.of("", "Northwind", "northwind"));

    @Test
    void shouldDrawBoxFrameWhenTerminalShowsUnicode() {

        // when
        String table = TextTable.framed(HEADERS, ROWS, true);

        // then
        assertThat(table.lines().toList()).containsExactly(
                "┌───┬───────────┬───────────┐",
                "│   │ NAME      │ SLUG      │",
                "├───┼───────────┼───────────┤",
                "│ * │ Acme HR   │ acme-hr   │",
                "├───┼───────────┼───────────┤",
                "│   │ Northwind │ northwind │",
                "└───┴───────────┴───────────┘");
    }

    @Test
    void shouldDrawPlainFrameWhenTerminalCannotShowUnicode() {

        // when
        String table = TextTable.framed(HEADERS, ROWS, false);

        // then
        assertThat(table.lines().toList()).containsExactly(
                "+---+-----------+-----------+",
                "|   | NAME      | SLUG      |",
                "+---+-----------+-----------+",
                "| * | Acme HR   | acme-hr   |",
                "+---+-----------+-----------+",
                "|   | Northwind | northwind |",
                "+---+-----------+-----------+");
    }

    @Test
    void shouldPrintTabSeparatedRowsWithoutHeaderForScripts() {

        // when
        String table = TextTable.plain(ROWS);

        // then
        assertThat(table.lines().toList()).containsExactly("*\tAcme HR\tacme-hr", "\tNorthwind\tnorthwind");
    }

    @Test
    void shouldPrintFrameWhenConsoleIsATerminal() {

        // given
        TestConsole testConsole = TestConsole.withCapabilities(new TerminalCapabilities(true, false, false, true));

        // when
        testConsole.console().table(HEADERS, ROWS);

        // then
        assertThat(testConsole.output()).startsWith("┌───┬");
    }
}
