package com.friendswine.client;

import com.friendswine.DollBlockEntity;
import com.friendswine.JellyAnimation;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.sound.sampled.AudioFormat;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/** One positional song, including clients that load the doll partway through playback. */
public final class DollMusicSound extends AbstractTickableSoundInstance {
    private final DollBlockEntity doll;
    private final Level level;
    private final long startTick;
    private volatile long elapsedTicks;

    public DollMusicSound(DollBlockEntity doll) {
        super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("friendswine", "doll_music")),
                SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.doll = doll;
        this.level = Objects.requireNonNull(doll.getLevel(), "Doll must belong to a level");
        this.startTick = doll.getStartTick();
        this.elapsedTicks = Math.max(0L, level.getGameTime() - startTick);
        this.x = doll.getBlockPos().getX() + 0.5;
        this.y = doll.getBlockPos().getY() + 0.5;
        this.z = doll.getBlockPos().getZ() + 0.5;
        this.looping = true;
        this.relative = false;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    public long getStartTick() {
        return startTick;
    }

    public boolean matchesPlayingDoll(DollBlockEntity candidate) {
        return candidate == doll && candidate.getStartTick() == startTick && candidate.isPlaying();
    }

    public void cancel() {
        stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return !isStopped() && !doll.isRemoved() && doll.getLevel() == level
                && matchesPlayingDoll(doll) && level.getBlockEntity(doll.getBlockPos()) == doll;
    }

    @Override
    public void tick() {
        elapsedTicks = Math.max(0L, level.getGameTime() - startTick);
        if (!canPlaySound()) {
            stop();
        }
    }

    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        return buffers.getStream(sound.getPath(), true).thenApplyAsync(stream -> {
            try {
                OffsetAudioStream shifted = new OffsetAudioStream(stream);
                // ponytail: decode-discard is bounded by one song plus load time; an index only helps much longer tracks.
                long target = elapsedTicks;
                long frame = JellyAnimation.musicFrame(target);
                shifted.skipToFrame(frame);
                while (target != elapsedTicks && !shifted.ended) {
                    long next = elapsedTicks;
                    // Keep this seek monotonic even when loading crosses the song's loop boundary.
                    frame += Math.max(0, next - target) * JellyAnimation.MUSIC_SAMPLE_RATE / 20;
                    shifted.skipToFrame(frame);
                    target = next;
                }
                return shifted;
            } catch (IOException | RuntimeException error) {
                try {
                    stream.close();
                } catch (IOException closeError) {
                    error.addSuppressed(closeError);
                }
                throw new CompletionException(error);
            }
        }, Util.nonCriticalIoPool());
    }

    private static final class OffsetAudioStream implements AudioStream {
        private final AudioStream source;
        private ByteBuffer pending;
        private long skippedBytes;
        private boolean ended;

        private OffsetAudioStream(AudioStream source) {
            this.source = source;
        }

        private void skipToFrame(long frame) throws IOException {
            int frameSize = getFormat().getFrameSize();
            if (frameSize <= 0) {
                throw new IOException("Audio stream has no PCM frame size");
            }
            long targetBytes = Math.max(0L, frame) * frameSize;
            while (skippedBytes < targetBytes && !ended) {
                if (pending == null || !pending.hasRemaining()) {
                    pending = source.read((int) Math.min(16384L, targetBytes - skippedBytes));
                    if (pending == null || !pending.hasRemaining()) {
                        ended = true;
                        break;
                    }
                }
                int skip = (int) Math.min(targetBytes - skippedBytes, pending.remaining());
                pending.position(pending.position() + skip);
                skippedBytes += skip;
            }
        }

        @Override
        public AudioFormat getFormat() {
            return source.getFormat();
        }

        @Override
        public ByteBuffer read(int count) throws IOException {
            if (pending != null && pending.hasRemaining()) {
                // JOrbis can return more than requested: retain every sample after the seek point.
                ByteBuffer result = pending;
                pending = null;
                return result;
            }
            return source.read(count);
        }

        @Override
        public void close() throws IOException {
            pending = null;
            source.close();
        }
    }

    /** Runnable check: an over-reading decoder must neither drop nor repeat samples while seeking. */
    public static void main(String[] args) throws IOException {
        for (int frame : new int[] {0, 3, 16, 20}) {
            class FakeStream implements AudioStream {
                int position;
                boolean closed;
                public AudioFormat getFormat() { return new AudioFormat(20, 16, 1, true, false); }
                public ByteBuffer read(int count) {
                    ByteBuffer bytes = ByteBuffer.allocate(Math.min(8, 32 - position));
                    while (bytes.hasRemaining()) bytes.put((byte) position++);
                    return bytes.flip();
                }
                public void close() { closed = true; }
            }
            FakeStream source = new FakeStream();
            try (OffsetAudioStream stream = new OffsetAudioStream(source)) {
                stream.skipToFrame(frame / 2);
                stream.skipToFrame(frame);
                int expected = Math.min(frame * 2, 32);
                ByteBuffer bytes;
                while ((bytes = stream.read(2)).hasRemaining()) {
                    while (bytes.hasRemaining()) {
                        if ((bytes.get() & 255) != expected++) throw new AssertionError("PCM seek mismatch");
                    }
                }
                if (expected != 32) throw new AssertionError("PCM tail was lost");
            }
            if (!source.closed) throw new AssertionError("PCM stream was not closed");
        }
        if (args.length == 1) {
            Path song = Path.of(args[0]);
            ByteBuffer pcm;
            try (JOrbisAudioStream decoder = new JOrbisAudioStream(Files.newInputStream(song))) {
                pcm = decoder.readAll();
                if (pcm.remaining() != JellyAnimation.MUSIC_FRAMES * decoder.getFormat().getFrameSize()) {
                    throw new AssertionError("Native decoder length differs from the animation/audio loop clock");
                }
            }
            for (long frame : new long[] {0, 1000000, JellyAnimation.MUSIC_FRAMES - 4L, JellyAnimation.MUSIC_FRAMES + 3L}) {
                try (OffsetAudioStream shifted = new OffsetAudioStream(
                        new LoopingAudioStream(JOrbisAudioStream::new, Files.newInputStream(song)))) {
                    shifted.skipToFrame(frame);
                    int matched = 0;
                    while (matched < 32) {
                        ByteBuffer bytes = shifted.read(32 - matched);
                        if (!bytes.hasRemaining()) throw new AssertionError("Looping stream ended");
                        while (bytes.hasRemaining() && matched < 32) {
                            int index = (int) ((frame * 2 + matched) % pcm.remaining());
                            if (bytes.get() != pcm.get(index)) throw new AssertionError("Native loop seek changed PCM samples");
                            matched++;
                        }
                    }
                }
            }
        }
        System.out.println("DollMusicSound PCM seek, native decoder length and seamless loop checks passed");
    }
}
