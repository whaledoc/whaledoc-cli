package io.whaledoc.console;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A table for the terminal, in a frame drawn with box characters, or with {@code +-|} where the terminal can't show
 * them:
 * <pre>
 * ┌───┬───────────┬───────────┐
 * │   │ NAME      │ SLUG      │
 * ├───┼───────────┼───────────┤
 * │ * │ Acme HR   │ acme-hr   │
 * ├───┼───────────┼───────────┤
 * │   │ Northwind │ northwind │
 * └───┴───────────┴───────────┘
 * </pre>
 * A line between rows keeps each row easy to follow across the columns.
 * Output that goes to a script gets {@link #plain}: one line per row, the cells separated by tabs, no header.
 */
final class TextTable {

    private static final Frame UNICODE = new Frame("─", "│", "┌", "┬", "┐", "├", "┼", "┤", "└", "┴", "┘");
    private static final Frame ASCII = new Frame("-", "|", "+", "+", "+", "+", "+", "+", "+", "+", "+");
    private static final String CELL_SEPARATOR = "\t";

    private TextTable() {
    }

    static String framed(List<String> headers, List<List<String>> rows, boolean unicode) {

        Frame frame = unicode ? UNICODE : ASCII;
        List<Integer> widths = widths(headers, rows);
        String separator = border(frame.middleLeft(), frame.cross(), frame.middleRight(), widths, frame);

        return Stream.of(
                        Stream.of(border(frame.topLeft(), frame.topJoin(), frame.topRight(), widths, frame),
                                line(headers, widths, frame)),
                        rows.stream().flatMap(row -> Stream.of(separator, line(row, widths, frame))),
                        Stream.of(border(frame.bottomLeft(), frame.bottomJoin(), frame.bottomRight(), widths, frame)))
                .flatMap(lines -> lines)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    static String plain(List<List<String>> rows) {

        return rows.stream()
                .map(row -> String.join(CELL_SEPARATOR, row))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static List<Integer> widths(List<String> headers, List<List<String>> rows) {

        return IntStream.range(0, headers.size())
                .mapToObj(column -> Stream.concat(Stream.of(headers), rows.stream())
                        .mapToInt(row -> row.get(column).length())
                        .max()
                        .orElse(0))
                .toList();
    }

    private static String border(String left, String join, String right, List<Integer> widths, Frame frame) {

        return widths.stream()
                .map(width -> frame.horizontal().repeat(width + 2))
                .collect(Collectors.joining(join, left, right));
    }

    private static String line(List<String> cells, List<Integer> widths, Frame frame) {

        return IntStream.range(0, cells.size())
                .mapToObj(column -> " " + pad(cells.get(column), widths.get(column)) + " ")
                .collect(Collectors.joining(frame.vertical(), frame.vertical(), frame.vertical()));
    }

    private static String pad(String text, int width) {

        return text + " ".repeat(width - text.length());
    }

    private record Frame(String horizontal, String vertical, String topLeft, String topJoin, String topRight,
                         String middleLeft, String cross, String middleRight,
                         String bottomLeft, String bottomJoin, String bottomRight) {
    }
}
