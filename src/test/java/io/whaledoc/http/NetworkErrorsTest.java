package io.whaledoc.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkErrorsTest {

    private static final URI API = URI.create("https://api.whaledoc.io/cli/webhooks/events");

    @Test
    void shouldExplainUnknownHostWhenAddressCannotBeResolved() {

        // given
        IOException error = createConnectException(new UnresolvedAddressException());

        // when
        String actual = NetworkErrors.describe(API, error);

        // then
        assertThat(actual).isEqualTo("Unable to find api.whaledoc.io. Check your internet connection.");
    }

    @Test
    void shouldAskIfServerIsRunningWhenConnectionIsRefused() {

        // given
        IOException error = createConnectException(new ClosedChannelException());

        // when
        String actual = NetworkErrors.describe(URI.create("http://localhost:8080/events"), error);

        // then
        assertThat(actual).isEqualTo("Unable to connect to localhost:8080. Is the server running?");
    }

    @Test
    void shouldReportTimeoutWhenConnectingTakesTooLong() {

        // given
        IOException error = new HttpConnectTimeoutException("HTTP connect timed out");

        // when
        String actual = NetworkErrors.describe(API, error);

        // then
        assertThat(actual).isEqualTo("Connecting to api.whaledoc.io timed out. Check your internet connection.");
    }

    @Test
    void shouldReportLostConnectionWhenCauseIsUnknown() {

        // given
        IOException error = new IOException("Stream reset");

        // when
        String actual = NetworkErrors.describe(API, error);

        // then
        assertThat(actual).isEqualTo("Lost the connection to api.whaledoc.io.");
    }

    // Java's HTTP client wraps the actual cause in ConnectExceptions without a message
    private IOException createConnectException(Throwable cause) {

        ConnectException inner = new ConnectException();
        inner.initCause(cause);

        ConnectException outer = new ConnectException();
        outer.initCause(inner);

        return outer;
    }
}
