package top.nalanquede.jukeboxmuse.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import top.nalanquede.jukeboxmuse.JukeboxMuse;

/**
 * Downloads a song into memory on its own thread as fast as the network allows, so the HTTP connection
 * only lives for a few seconds instead of the whole song. If the connection drops it resumes with a
 * Range request. The decoder reads through {@link #stream()}, which blocks until bytes arrive.
 */
final class SongDownload implements Runnable {
    private static final int MAX_ATTEMPTS = 4;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private final String url;
    private byte[] data = new byte[4 * 1024 * 1024];
    private int size;
    private boolean done;
    private IOException error;
    private volatile boolean cancelled;

    private SongDownload(String url) {
        this.url = url;
    }

    static SongDownload start(String url) {
        SongDownload d = new SongDownload(url);
        Thread t = new Thread(d, "JukeboxMuse-Download");
        t.setDaemon(true);
        t.start();
        return d;
    }

    void cancel() {
        cancelled = true;
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void run() {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && !cancelled; attempt++) {
            try {
                fetchFrom(size());
                finish(null);
                return;
            } catch (IOException e) {
                last = e;
                JukeboxMuse.LOGGER.warn("Song download interrupted at {} bytes (attempt {}/{}): {}", size(), attempt, MAX_ATTEMPTS, e.toString());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    break;
                }
            } catch (Exception e) {
                last = new IOException(e);
                break;
            }
        }
        finish(last != null ? last : new IOException("cancelled"));
    }

    private void fetchFrom(int offset) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0")
                .GET();
        if (offset > 0) req.header("Range", "bytes=" + offset + "-");
        HttpResponse<InputStream> response = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status >= 400) {
            response.body().close();
            // Not a network hiccup: retrying will not help
            throw new IllegalStateException("HTTP " + status);
        }
        try (InputStream in = response.body()) {
            // A server that ignores Range answers 200 with the whole file: drop what we already have
            long skip = offset > 0 && status != 206 ? offset : 0;
            byte[] buf = new byte[64 * 1024];
            int n;
            while (!cancelled && (n = in.read(buf)) != -1) {
                int from = 0;
                if (skip > 0) {
                    int s = (int) Math.min(skip, n);
                    skip -= s;
                    from = s;
                }
                if (n > from) append(buf, from, n - from);
            }
        }
    }

    private synchronized int size() {
        return size;
    }

    private synchronized void append(byte[] buf, int off, int len) throws IOException {
        if (size + len > MAX_BYTES) throw new IOException("song larger than " + MAX_BYTES + " bytes");
        if (size + len > data.length) {
            byte[] bigger = new byte[Math.min(MAX_BYTES, Math.max(data.length * 2, size + len))];
            System.arraycopy(data, 0, bigger, 0, size);
            data = bigger;
        }
        System.arraycopy(buf, off, data, size, len);
        size += len;
        notifyAll();
    }

    private synchronized void finish(IOException failure) {
        done = true;
        error = failure;
        notifyAll();
    }

    /** Reads the downloaded bytes in order, waiting for more while the download is still running. */
    InputStream stream() {
        return new InputStream() {
            private int pos;

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : one[0] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) return 0;
                synchronized (SongDownload.this) {
                    while (pos >= size && !done && !cancelled) {
                        try {
                            SongDownload.this.wait(200);
                        } catch (InterruptedException e) {
                            throw new IOException(e);
                        }
                    }
                    if (pos < size) {
                        int n = Math.min(len, size - pos);
                        System.arraycopy(data, pos, b, off, n);
                        pos += n;
                        return n;
                    }
                    if (error != null && !cancelled) throw error;
                    return -1;
                }
            }

            @Override
            public void close() {
                cancel();
            }
        };
    }
}
