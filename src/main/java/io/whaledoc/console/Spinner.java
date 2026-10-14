package io.whaledoc.console;

/**
 * Shows that the CLI is busy. It only animates in an interactive terminal; in a pipe or CI log it prints
 * its message once instead of a new frame every 100 milliseconds. Either way it writes to standard error, so piped
 * output only carries the command's results.
 */
public final class Spinner {

    private static final String[] UNICODE_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String[] ASCII_FRAMES = {"|", "/", "-", "\\"};
    private static final String CLEAR_LINE = "\r\u001B[2K";
    private static final String CURSOR_UP = "\u001B[1A";

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
            console.status(message);
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
        console.printStatus(CLEAR_LINE);
    }

    /**
     * The terminal echoed the user's Enter as a new line under the spinner. Goes back up to the spinner's own line and
     * clears it, so the animation carries on there instead of leaving its last frame behind.
     */
    public void returnAfterEnter() {

        if (!running) {
            return;
        }

        console.printStatus(CURSOR_UP + CLEAR_LINE);
    }

    private void animate() {

        String[] frames = console.supportsUnicode() ? UNICODE_FRAMES : ASCII_FRAMES;

        for (int frame = 0; running; frame = (frame + 1) % frames.length) {

            console.printStatus("\r" + console.brand(frames[frame]) + " " + message);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
