package io.whaledoc.console;

/**
 * Shows that the CLI is busy. It only animates in an interactive terminal; in a pipe or CI log it prints
 * its message once instead of a new frame every 100 milliseconds.
 */
public final class Spinner {

    private static final String[] UNICODE_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String[] ASCII_FRAMES = {"|", "/", "-", "\\"};
    private static final String CLEAR_LINE = "\r\u001B[2K";

    private final Console console;
    private final String message;

    private volatile boolean running;
    private Thread thread;

    Spinner(Console console, String message) {
        this.console = console;
        this.message = message;
    }

    public void start() {

        if (!console.isInteractive()) {
            console.println(message);
            return;
        }

        running = true;
        thread = Thread.ofVirtual().start(this::animate);
    }

    public void stop() {

        if (thread == null) {
            return;
        }

        running = false;
        thread.interrupt();

        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        thread = null;
        console.print(CLEAR_LINE);
    }

    private void animate() {

        String[] frames = console.supportsUnicode() ? UNICODE_FRAMES : ASCII_FRAMES;

        for (int frame = 0; running; frame = (frame + 1) % frames.length) {

            console.print("\r" + console.brand(frames[frame]) + " " + message);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
