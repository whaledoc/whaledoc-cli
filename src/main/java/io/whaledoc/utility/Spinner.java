package io.whaledoc.utility;

import picocli.CommandLine;

public class Spinner {

    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private final String message;
    private volatile boolean running;
    private Thread thread;

    public Spinner(String message) {
        this.message = message;
    }

    public void start() {
        running = true;

        thread = Thread.ofVirtual().start(() -> {
            int frame = 0;

            while (running) {
                System.out.print(
                        "\r" + CommandLine.Help.Ansi.ON.string("@|bold " + FRAMES[frame] + "|@") + " " + message
                );
                System.out.flush();

                frame = (frame + 1) % FRAMES.length;

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void stop() {
        running = false;

        if (thread != null) {
            thread.interrupt();
        }

        System.out.print("\r\033[2K");
        System.out.flush();
    }
}
