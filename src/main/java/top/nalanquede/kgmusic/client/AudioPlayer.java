package top.nalanquede.kgmusic.client;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import top.nalanquede.kgmusic.KgConfig;
import top.nalanquede.kgmusic.KgMusic;

/**
 * Plays an MP3 URL: {@link SongDownload} (whole file into memory) → JLayer decode → OpenAL streaming source. Uses OpenAL directly (not javax.sound)
 * so it also works on Android launchers like FCL, which only provide OpenAL.
 * One background thread per song; the source is head-relative so the player's position does not matter.
 */
public final class AudioPlayer {
    // 16 × 250 ms = 4 s of decoded audio queued ahead, to ride out short network stalls
    private static final int BUFFER_COUNT = 16;
    private static final int BUFFER_MS = 250;
    // Buffers needed before (re)starting playback
    private static final int PREBUFFER = 4;

    private static Worker worker;

    private AudioPlayer() {
    }

    /** Start url, skipping the first offsetMs (measured at the moment the server sent it). */
    public static synchronized void play(String url, long offsetMs) {
        stop();
        worker = new Worker(url, offsetMs, System.currentTimeMillis());
        Thread t = new Thread(worker, "KgMusic-Audio");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void stop() {
        if (worker != null) {
            worker.stop();
            worker = null;
        }
    }

    /** True while url is still being streamed (a failed or finished stream returns false so it can be restarted). */
    public static synchronized boolean isPlaying(String url) {
        return worker != null && !worker.finished && worker.url.equals(url);
    }

    /** Current playback position in ms, or -1 while nothing is audible yet. */
    public static synchronized long positionMs() {
        return worker == null ? -1 : worker.positionMs;
    }

    private static float gain() {
        if (KgConfig.MUTED.get()) return 0f;
        var options = Minecraft.getInstance().options;
        return (float) (KgConfig.VOLUME.get() * options.getSoundSourceVolume(SoundSource.MASTER) * options.getSoundSourceVolume(SoundSource.RECORDS));
    }

    private static final class Worker implements Runnable {
        private final String url;
        private final long offsetMs;
        private final long receivedAt;
        volatile boolean stopped;
        volatile boolean finished;
        private volatile SongDownload download;
        volatile long positionMs = -1;

        Worker(String url, long offsetMs, long receivedAt) {
            this.url = url;
            this.offsetMs = offsetMs;
            this.receivedAt = receivedAt;
        }

        @Override
        public void run() {
            int source = 0;
            int[] buffers = new int[BUFFER_COUNT];
            download = SongDownload.start(url);
            if (stopped) download.cancel();
            try (InputStream in = download.stream()) {
                Bitstream bitstream = new Bitstream(new BufferedInputStream(in, 64 * 1024));
                Decoder decoder = new Decoder();
                // Catch up with however long the download took to start
                double skipMs = offsetMs + (System.currentTimeMillis() - receivedAt);
                double decodedMs = 0;

                source = AL10.alGenSources();
                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
                AL10.alGenBuffers(buffers);

                // Skip frames without decoding until we reach the shared playback position
                Header pending = null;
                while (!stopped) {
                    Header h = bitstream.readFrame();
                    if (h == null) break;
                    if (decodedMs + h.ms_per_frame() >= skipMs) {
                        pending = h;
                        break;
                    }
                    decodedMs += h.ms_per_frame();
                    bitstream.closeFrame();
                }
                if (stopped) return;

                double playedBaseMs = decodedMs;
                Chunk chunk = new Chunk();
                int[] free = buffers.clone();
                int freeCount = BUFFER_COUNT;
                // Duration of each queued buffer, oldest first
                ArrayDeque<Double> queuedMs = new ArrayDeque<>();
                boolean eof = false;

                while (!stopped) {
                    // Reclaim every finished buffer. After an underrun the source is STOPPED and all queued
                    // buffers count as processed; they must be unqueued before alSourcePlay, which would
                    // otherwise replay the whole queue from its first (already heard) buffer.
                    for (int n = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED); n > 0; n--) {
                        free[freeCount++] = AL10.alSourceUnqueueBuffers(source);
                        playedBaseMs += queuedMs.removeFirst();
                    }

                    if (!eof && freeCount > 0) {
                        // Decode ~BUFFER_MS of audio into one free buffer
                        chunk.reset();
                        while (!stopped && chunk.ms < BUFFER_MS) {
                            Header header = pending != null ? pending : bitstream.readFrame();
                            pending = null;
                            if (header == null) break;
                            SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                            bitstream.closeFrame();
                            chunk.append(out);
                        }
                        if (chunk.length > 0) {
                            int buffer = free[--freeCount];
                            AL10.alBufferData(buffer, chunk.channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, chunk.data(), chunk.frequency);
                            AL10.alSourceQueueBuffers(source, buffer);
                            queuedMs.addLast(chunk.ms);
                        }
                        if (chunk.ms < BUFFER_MS) eof = true;
                    }

                    if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                        if (queuedMs.isEmpty()) {
                            if (eof) break; // played to the end
                        } else if (eof || queuedMs.size() >= PREBUFFER) {
                            // First start, or resume after an underrun once enough is buffered again
                            AL10.alSourcePlay(source);
                        }
                    }
                    updateSource(source, playedBaseMs);
                    if (eof || freeCount == 0) Thread.sleep(20);
                }
            } catch (InterruptedException ignored) {
                // Stopped
            } catch (Exception e) {
                // Full stack trace: JLayer wraps the real network error as the cause
                KgMusic.LOGGER.warn("Song playback failed", e);
            } finally {
                finished = true;
                positionMs = -1;
                if (source != 0) {
                    AL10.alSourceStop(source);
                    AL10.alSourcei(source, AL10.AL_BUFFER, 0);
                    AL10.alDeleteSources(source);
                    AL10.alDeleteBuffers(buffers);
                }
            }
        }

        void stop() {
            stopped = true;
            // Wake a read blocked on the download so the old song's source is released promptly
            if (download != null) download.cancel();
        }

        private void updateSource(int source, double playedBaseMs) {
            AL10.alSourcef(source, AL10.AL_GAIN, gain());
            positionMs = (long) (playedBaseMs + AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET) * 1000);
        }
    }

    /** Accumulates decoded PCM frames until there is enough for one OpenAL buffer. */
    private static final class Chunk {
        private short[] samples = new short[48000 * 2];
        int length;
        int channels = 2;
        int frequency = 44100;
        double ms;

        void reset() {
            length = 0;
            ms = 0;
        }

        void append(SampleBuffer out) {
            channels = out.getChannelCount();
            frequency = out.getSampleFrequency();
            int n = out.getBufferLength();
            if (length + n > samples.length) {
                short[] bigger = new short[Math.max(samples.length * 2, length + n)];
                System.arraycopy(samples, 0, bigger, 0, length);
                samples = bigger;
            }
            System.arraycopy(out.getBuffer(), 0, samples, length, n);
            length += n;
            ms = length * 1000.0 / (channels * (double) frequency);
        }

        ShortBuffer data() {
            ShortBuffer buf = BufferUtils.createShortBuffer(length);
            buf.put(samples, 0, length).flip();
            return buf;
        }
    }
}
