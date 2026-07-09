package myau.ui.mainmenu;

import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoPlayer {
    private static final String RESOURCE_PATH = "/assets/myau/bg/mainmenu.mp4";
    private static final long DEFAULT_FRAME_DELAY_NANOS = 16_666_667L;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final AtomicInteger generation = new AtomicInteger();
    private final Object frameLock = new Object();

    private Thread decodeThread;
    private DynamicTexture texture;
    private ResourceLocation textureLocation;
    private int textureWidth;
    private int textureHeight;
    private VideoFrame pendingFrame;
    private boolean pendingReady;
    private volatile boolean running;
    private volatile boolean unavailable;

    public void render(int width, int height) {
        ensureStarted();
        updateTexture();

        if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
            drawCover(width, height);
        }
    }

    public void reset() {
        unavailable = false;
        ensureStarted();
    }

    public void stop() {
        generation.incrementAndGet();
        running = false;

        Thread thread = decodeThread;
        if (thread != null) {
            thread.interrupt();
        }

        synchronized (frameLock) {
            if (pendingFrame != null) {
                pendingFrame.dispose();
                pendingFrame = null;
            }
            pendingReady = false;
        }

        if (textureLocation != null) {
            mc.getTextureManager().deleteTexture(textureLocation);
            textureLocation = null;
            texture = null;
            textureWidth = 0;
            textureHeight = 0;
        }
    }

    private void ensureStarted() {
        synchronized (this) {
            if (unavailable || running) {
                return;
            }

            int id = generation.incrementAndGet();
            running = true;
            decodeThread = new Thread(() -> decodeLoop(id), "Adjust-MainMenu-Video");
            decodeThread.setDaemon(true);
            decodeThread.start();
        }
    }

    private void decodeLoop(int id) {
        try {
            File videoFile = resolveVideoFile();
            while (isActive(id)) {
                playOnce(videoFile, id);
            }
        } catch (Throwable ignored) {
            if (isActive(id)) {
                unavailable = true;
            }
        } finally {
            synchronized (this) {
                if (decodeThread == Thread.currentThread()) {
                    decodeThread = null;
                }
                if (generation.get() == id) {
                    running = false;
                }
            }
        }
    }

    private void playOnce(File videoFile, int id) throws Exception {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);
        Java2DFrameConverter converter = new Java2DFrameConverter();
        VideoFrame writeFrame = null;

        try {
            grabber.setAudioStream(-1);
            grabber.setVideoOption("threads", "0");
            grabber.start();

            double sourceFps = grabber.getFrameRate();
            long frameDelay = getFrameDelayNanos(sourceFps);

            while (isActive(id)) {
                long frameStarted = System.nanoTime();
                Frame frame = grabber.grabImage();
                if (frame == null) {
                    return;
                }

                BufferedImage image = converter.convert(frame);
                if (image != null) {
                    if (!isActive(id)) {
                        break;
                    }

                    if (writeFrame == null || writeFrame.width != image.getWidth() || writeFrame.height != image.getHeight()) {
                        if (writeFrame != null) {
                            writeFrame.dispose();
                        }
                        writeFrame = new VideoFrame(image.getWidth(), image.getHeight());
                    }

                    writeFrame.graphics.drawImage(image, 0, 0, null);
                    if (!isActive(id)) {
                        break;
                    }
                    writeFrame = publishFrame(writeFrame);
                }

                sleepFrame(frameDelay - (System.nanoTime() - frameStarted));
            }
        } finally {
            if (writeFrame != null) {
                writeFrame.dispose();
            }
            try {
                grabber.stop();
            } catch (Throwable ignored) {
            }
            try {
                grabber.release();
            } catch (Throwable ignored) {
            }
            try {
                converter.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean isActive(int id) {
        return running && generation.get() == id && !Thread.currentThread().isInterrupted();
    }

    private long getFrameDelayNanos(double fps) {
        if (fps < 1.0D || fps > 240.0D) {
            return DEFAULT_FRAME_DELAY_NANOS;
        }
        return (long) (1_000_000_000D / fps);
    }

    private VideoFrame publishFrame(VideoFrame frame) {
        synchronized (frameLock) {
            VideoFrame reusable = pendingFrame;
            pendingFrame = frame;
            pendingReady = true;
            if (reusable != null && reusable.width == frame.width && reusable.height == frame.height) {
                return reusable;
            }
            if (reusable != null) {
                reusable.dispose();
            }
        }

        if (frame.width > 0 && frame.height > 0) {
            return new VideoFrame(frame.width, frame.height);
        }
        return null;
    }

    private void sleepFrame(long nanos) {
        if (nanos <= 0L) {
            return;
        }
        try {
            Thread.sleep(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateTexture() {
        synchronized (frameLock) {
            if (!pendingReady || pendingFrame == null || pendingFrame.width <= 0 || pendingFrame.height <= 0) {
                return;
            }

            if (texture == null || textureWidth != pendingFrame.width || textureHeight != pendingFrame.height) {
                textureWidth = pendingFrame.width;
                textureHeight = pendingFrame.height;
                texture = new DynamicTexture(textureWidth, textureHeight);
                textureLocation = mc.getTextureManager().getDynamicTextureLocation("adjust_mainmenu_video", texture);
            }

            System.arraycopy(pendingFrame.pixels, 0, texture.getTextureData(), 0, textureWidth * textureHeight);
            texture.updateDynamicTexture();
            pendingReady = false;
        }
    }

    private void drawCover(int width, int height) {
        float sourceAspect = textureWidth / (float) textureHeight;
        float screenAspect = width / (float) height;
        float drawWidth = width;
        float drawHeight = height;

        if (screenAspect > sourceAspect) {
            drawHeight = drawWidth / sourceAspect;
        } else {
            drawWidth = drawHeight * sourceAspect;
        }

        RenderUtil.drawImage(textureLocation, (width - drawWidth) / 2.0F, (height - drawHeight) / 2.0F, drawWidth, drawHeight, 0xFFFFFFFF);
    }

    private File resolveVideoFile() throws Exception {
        File sourceFile = new File("src/main/resources/assets/myau/bg/mainmenu.mp4");
        if (sourceFile.exists()) {
            return sourceFile;
        }

        File runFile = new File("assets/myau/bg/mainmenu.mp4");
        if (runFile.exists()) {
            return runFile;
        }

        try (InputStream input = getClass().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("mainmenu.mp4 not found");
            }
            File temp = File.createTempFile("myau-mainmenu", ".mp4");
            temp.deleteOnExit();
            Files.copy(input, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return temp;
        }
    }

    private static final class VideoFrame {
        private final BufferedImage image;
        private final Graphics2D graphics;
        private final int[] pixels;
        private final int width;
        private final int height;

        private VideoFrame(int width, int height) {
            this.width = width;
            this.height = height;
            this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            this.graphics = image.createGraphics();
            this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        }

        private void dispose() {
            graphics.dispose();
        }
    }
}
