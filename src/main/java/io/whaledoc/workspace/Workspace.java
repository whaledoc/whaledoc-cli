package io.whaledoc.workspace;

/**
 * A workspace the CLI may work in; {@code current} marks the one it works in now. Names need not be unique; slugs are,
 * so you switch by slug, and the API by ID.
 */
public record Workspace(String id, String slug, String name, boolean current) {
}
