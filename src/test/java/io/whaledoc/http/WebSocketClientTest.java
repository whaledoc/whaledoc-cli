package io.whaledoc.http;

import io.whaledoc.exceptions.ApiException;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketClientTest {

    private static final String TOKEN = "access-token";
    private static final Duration WAIT = Duration.ofSeconds(10);
    /** Long enough for the client's first reconnect attempt to find nothing listening. */
    private static final Duration SERVER_DOWN = Duration.ofMillis(500);

    private final WebSocketClient client = new WebSocketClient(HttpClient.newHttpClient());
    private final List<TestServer> servers = new CopyOnWriteArrayList<>();
    private WebSocketConnection connection;

    @AfterEach
    void tearDown() throws InterruptedException {

        if (connection != null) {
            connection.close();
        }

        for (TestServer server : servers) {
            server.stop(1_000);
        }
    }

    @Test
    void shouldDeliverMessagesAndSendTokenAndVersionWhenConnected() throws Exception {

        // given
        TestServer server = startServer(freePort(), (socket, number) -> socket.send("hello"));
        Received received = new Received();

        // when
        connection = client.connect(server.uri(), TOKEN, received);
        connection.connected().get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(received.next()).isEqualTo("hello");
        assertThat(server.headers).containsEntry("Authorization", "Bearer " + TOKEN).containsEntry("API-Version", "1");
    }

    @Test
    void shouldFailWithoutRetryingWhenHandshakeIsRefused() throws Exception {

        // given
        TestServer server = startServer(freePort(), (socket, number) -> { });
        server.refuse = true;

        // when
        connection = client.connect(server.uri(), TOKEN, new Received());
        Throwable thrown = connection.connected().handle((ok, error) -> error).get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(thrown).hasCauseInstanceOf(ApiException.class);
        assertThat(server.handshakes.get()).isEqualTo(1);
    }

    @Test
    void shouldReconnectAtOnceWhenServerClosesConnection() throws Exception {

        // given
        TestServer server = startServer(freePort(), (socket, number) -> {
            if (number == 1) {
                socket.close(CloseFrame.GOING_AWAY);
            }
        });
        Received received = new Received();

        // when
        connection = client.connect(server.uri(), TOKEN, received);

        // then
        assertThat(received.reconnected.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        assertThat(received.reconnects).first().extracting(Reconnect::delay).isEqualTo(Duration.ZERO);
        assertThat(server.handshakes.get()).isEqualTo(2);
    }

    @Test
    void shouldKeepRetryingWhenServerIsDownAfterConnectionDropped() throws Exception {

        // given
        int port = freePort();
        TestServer first = startServer(port, (socket, number) -> { });
        Received received = new Received();
        connection = client.connect(first.uri(), TOKEN, received);
        connection.connected().get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // when
        first.stop(1_000);
        sleep(SERVER_DOWN);
        startServer(port, (socket, number) -> { });

        // then
        assertThat(received.reconnected.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        assertThat(connection.isOpen()).isTrue();
    }

    @Test
    void shouldHandleMessagesBeforeReconnectingWhenServerClosesAfterThem() throws Exception {

        // given
        TestServer server = startServer(freePort(), (socket, number) -> {
            socket.send("disconnected");
            socket.close(CloseFrame.NORMAL);
        });
        CompletableFuture<Void> stopped = new CompletableFuture<>();

        // when
        connection = client.connect(server.uri(), TOKEN, message -> {
            sleep(Duration.ofMillis(300));
            connection.close();
            stopped.complete(null);
        });
        stopped.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        sleep(Duration.ofMillis(500));

        // then
        assertThat(server.handshakes.get()).isEqualTo(1);
    }

    @Test
    void shouldSendNormalCloseWhenConnectionIsClosed() throws Exception {

        // given
        TestServer server = startServer(freePort(), (socket, number) -> { });
        connection = client.connect(server.uri(), TOKEN, new Received());
        connection.connected().get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // when
        connection.close();

        // then
        assertThat(server.closeCodes.poll(WAIT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(CloseFrame.NORMAL);
    }

    /** What the client handed the listener: each message, the reconnects announced, and the first reconnected. */
    private static final class Received implements MessageListener {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final List<Reconnect> reconnects = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Boolean> reconnected = new CompletableFuture<>();

        @Override
        public void onMessage(String message) {

            messages.add(message);
        }

        @Override
        public void onReconnecting(Reconnect reconnect) {

            reconnects.add(reconnect);
        }

        @Override
        public void onReconnected() {

            reconnected.complete(true);
        }

        String next() throws InterruptedException {

            return messages.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
    }

    /** A WebSocket server that counts handshakes, keeps the first one's headers and runs {@code onOpen} per connection. */
    private static final class TestServer extends WebSocketServer {

        private final BiConsumer<WebSocket, Integer> onOpen;
        private final AtomicInteger handshakes = new AtomicInteger();
        private final Map<String, String> headers = new ConcurrentHashMap<>();
        private final BlockingQueue<Integer> closeCodes = new LinkedBlockingQueue<>();
        private final CompletableFuture<Void> started = new CompletableFuture<>();
        private volatile boolean refuse;

        private TestServer(int port, BiConsumer<WebSocket, Integer> onOpen) {

            super(new InetSocketAddress("localhost", port));
            this.onOpen = onOpen;
            setReuseAddr(true);
        }

        URI uri() {

            return URI.create("ws://localhost:%d/cli/webhooks/events".formatted(getPort()));
        }

        @Override
        public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket socket, Draft draft,
                                                                          ClientHandshake request)
                throws InvalidDataException {

            handshakes.incrementAndGet();
            headers.putIfAbsent("Authorization", request.getFieldValue("Authorization"));
            headers.putIfAbsent("API-Version", request.getFieldValue("API-Version"));

            if (refuse) {
                throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "Refused");
            }

            return super.onWebsocketHandshakeReceivedAsServer(socket, draft, request);
        }

        @Override
        public void onOpen(WebSocket socket, ClientHandshake handshake) {

            onOpen.accept(socket, handshakes.get());
        }

        @Override
        public void onClose(WebSocket socket, int code, String reason, boolean remote) {

            closeCodes.add(code);
        }

        @Override
        public void onMessage(WebSocket socket, String message) {
            // The tests send nothing that needs an answer
        }

        @Override
        public void onError(WebSocket socket, Exception error) {
            // A test that cares checks what the client saw
        }

        @Override
        public void onStart() {

            started.complete(null);
        }
    }

    private TestServer startServer(int port, BiConsumer<WebSocket, Integer> onOpen) throws Exception {

        TestServer server = new TestServer(port, onOpen);
        servers.add(server);
        server.start();
        server.started.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        return server;
    }

    private static int freePort() throws Exception {

        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void sleep(Duration duration) {

        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
