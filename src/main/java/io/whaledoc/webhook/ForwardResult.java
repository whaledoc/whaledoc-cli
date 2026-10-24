package io.whaledoc.webhook;

/** What the local endpoint did with a forwarded event: its answer, or why there was none. */
public sealed interface ForwardResult {

    long durationMs();

    /** The local endpoint answered; {@code body} is its start, as much as WhaleDoc keeps. */
    record Answered(int status, String body, long durationMs) implements ForwardResult {

        public boolean isSuccess() {

            return status >= 200 && status < 300;
        }
    }

    /** No answer: it took longer than the wait, or it couldn't be reached. {@code reason} is shown to the user. */
    record Unanswered(Kind kind, String reason, long durationMs) implements ForwardResult {
    }

    /** The names WhaleDoc uses for a missing answer. */
    enum Kind {
        TIMEOUT,
        CONNECTION_FAILED
    }
}
