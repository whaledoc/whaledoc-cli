package io.whaledoc.commands.workspace;

import io.whaledoc.console.Console;
import io.whaledoc.workspace.Workspace;

import java.util.List;

/** Workspaces as a table with NAME and SLUG, the current one marked with {@code *}. */
final class WorkspaceTable {

    private static final String CURRENT_MARK = "*";
    private static final List<String> HEADERS = List.of("", "NAME", "SLUG");

    private WorkspaceTable() {
    }

    static void print(List<Workspace> workspaces, Console console) {

        console.table(HEADERS, workspaces.stream().map(WorkspaceTable::row).toList());
    }

    private static List<String> row(Workspace workspace) {

        return List.of(workspace.current() ? CURRENT_MARK : "", workspace.name(), workspace.slug());
    }
}
