package io.whaledoc.webhook;

import java.util.Optional;

/**
 * What the local endpoint did with a forwarded event: its {@code status} and the start of its {@code body} when it
 * answered, else why not.
 */
public record ForwardResult(Optional<Integer> status, Optional<String> body, long durationMs, Optional<Failure> failure) {

    /** No answer: it took longer than the wait, or it couldn't be reached. {@code reason} is shown to the user. */
    public record Failure(Kind kind, String reason) {
    }

    /** The names WhaleDoc uses for a missing answer. */
    public enum Kind {
        TIMEOUT,
        CONNECTION_FAILED
    }

    public static ForwardResult answered(int status, String body, long durationMs) {

        return new ForwardResult(Optional.of(status), Optional.of(body), durationMs, Optional.empty());
    }

    public static ForwardResult failed(Kind kind, String reason, long durationMs) {

        return new ForwardResult(Optional.empty(), Optional.empty(), durationMs, Optional.of(new Failure(kind, reason)));
    }

    public boolean isSuccess() {

        return status.filter(code -> code >= 200 && code < 300).isPresent();
    }
}
