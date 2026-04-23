/**
 * src/OpenCvBridge.java
 *
 * Reflection-based bridge to OpenCV's Java bindings. Centralizes native-library loading,
 * VideoCapture lifecycle, and frame decoding so callers (CameraPreviewDialog, Main) don't
 * have to touch Class.forName / Method.invoke boilerplate.
 *
 * Built on top of the original capture logic previously inlined in Main.java.
 * 
 * @since 2026-04-23
 */
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import javax.imageio.*;

public final class OpenCvBridge {
    private static final String OPENCV_JAR_PATH = "lib/opencv-4.5.1-2.jar";

    private static boolean libraryLoaded = false;
    private static boolean loadAttempted = false;
    private static String lastError = "Webcam capture has not been attempted yet.";
    private static ClassLoader openCvClassLoader = OpenCvBridge.class.getClassLoader();

    private final Object capture;
    private final Class<?> matClass;
    private final Class<?> matOfByteClass;
    private final Class<?> imgcodecsClass;
    private final Method readMethod;
    private final Method releaseMethod;
    private final Method isOpenedMethod;
    private final Method emptyMethod;
    private final Method imencodeMethod;
    private final Object frame;
    private boolean released = false;

    private OpenCvBridge(Object capture, Class<?> videoCaptureClass, Class<?> matClass,
                         Class<?> matOfByteClass, Class<?> imgcodecsClass)
            throws ReflectiveOperationException {
        this.capture = capture;
        this.matClass = matClass;
        this.matOfByteClass = matOfByteClass;
        this.imgcodecsClass = imgcodecsClass;
        this.readMethod = videoCaptureClass.getMethod("read", matClass);
        this.releaseMethod = videoCaptureClass.getMethod("release");
        this.isOpenedMethod = videoCaptureClass.getMethod("isOpened");
        this.emptyMethod = matClass.getMethod("empty");
        this.imencodeMethod = imgcodecsClass.getMethod("imencode", String.class, matClass, matOfByteClass);
        this.frame = matClass.getConstructor().newInstance();
    }

    /**
     * Opens the default system camera, trying a series of backends until one succeeds.
     * Returns null and sets {@link #getLastError()} if no backend works.
     */
    public static OpenCvBridge open() {
        try {
            if (!ensureLoaded()) {
                return null;
            }

            Class<?> videoCaptureClass = loadClass("org.opencv.videoio.VideoCapture");
            Class<?> videoioClass = loadClass("org.opencv.videoio.Videoio");
            Class<?> matClass = loadClass("org.opencv.core.Mat");
            Class<?> matOfByteClass = loadClass("org.opencv.core.MatOfByte");
            Class<?> imgcodecsClass = loadClass("org.opencv.imgcodecs.Imgcodecs");

            Method isOpened = videoCaptureClass.getMethod("isOpened");
            Method release = videoCaptureClass.getMethod("release");

            for (int backend : backendCandidates(videoioClass)) {
                Object cap = openCamera(videoCaptureClass, backend);
                if (cap == null) {
                    continue;
                }
                if (!Boolean.TRUE.equals(isOpened.invoke(cap))) {
                    release.invoke(cap);
                    continue;
                }
                OpenCvBridge candidate = new OpenCvBridge(cap, videoCaptureClass, matClass, matOfByteClass, imgcodecsClass);
                if (!candidate.warmUp()) {
                    candidate.release();
                    continue;
                }
                lastError = null;
                return candidate;
            }

            lastError = "The system camera opened but produced no frames. "
                + "On Windows 11, check Settings → Privacy & security → Camera and make sure "
                + "\"Let desktop apps access your camera\" is turned on.";
            return null;
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | MalformedURLException e) {
            lastError = "Webcam capture unavailable: " + e.getMessage();
            System.err.println(lastError);
            return null;
        } catch (LinkageError e) {
            lastError = "OpenCV native library unavailable: " + e.getMessage();
            System.err.println(lastError);
            return null;
        }
    }

    /**
     * Reads one frame from the camera and returns it as a BufferedImage, or null if
     * the frame was empty or could not be decoded.
     */
    public BufferedImage readFrame() {
        if (released) {
            return null;
        }
        try {
            if (!Boolean.TRUE.equals(readMethod.invoke(capture, frame))) {
                return null;
            }
            if (Boolean.TRUE.equals(emptyMethod.invoke(frame))) {
                return null;
            }
            Object buffer = matOfByteClass.getConstructor().newInstance();
            if (!Boolean.TRUE.equals(imencodeMethod.invoke(null, ".png", frame, buffer))) {
                return null;
            }
            byte[] bytes = (byte[]) matOfByteClass.getMethod("toArray").invoke(buffer);
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                return ImageIO.read(in);
            }
        } catch (ReflectiveOperationException | IOException e) {
            lastError = "Frame read failed: " + e.getMessage();
            return null;
        }
    }

    /**
     * Tries to read at least one real frame within ~1.5 seconds. Returns false if the camera
     * opened but produces no usable frames — which is the common MSMF failure mode on Windows.
     */
    boolean warmUp() {
        for (int i = 0; i < 15; i++) {
            if (readFrame() != null) {
                return true;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public boolean isOpen() {
        if (released) return false;
        try {
            return Boolean.TRUE.equals(isOpenedMethod.invoke(capture));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public void release() {
        if (released) return;
        released = true;
        try {
            releaseMethod.invoke(capture);
        } catch (ReflectiveOperationException e) {
            System.err.println("Failed to release camera: " + e.getMessage());
        }
    }

    public static String getLastError() {
        return lastError;
    }

    private static Object openCamera(Class<?> videoCaptureClass, int backend)
            throws ReflectiveOperationException {
        try {
            return videoCaptureClass.getConstructor(int.class, int.class).newInstance(0, backend);
        } catch (NoSuchMethodException ignored) {
            return backend == -1 ? videoCaptureClass.getConstructor(int.class).newInstance(0) : null;
        }
    }

    private static int[] backendCandidates(Class<?> videoioClass) {
        try {
            return new int[]{
                videoioClass.getField("CAP_DSHOW").getInt(null),
                videoioClass.getField("CAP_ANY").getInt(null),
                videoioClass.getField("CAP_MSMF").getInt(null)
            };
        } catch (ReflectiveOperationException e) {
            return new int[]{-1};
        }
    }

    private static boolean ensureLoaded() {
        if (libraryLoaded) return true;
        if (loadAttempted) return false;
        loadAttempted = true;

        try {
            openCvClassLoader = getClassLoader();
            Class<?> loaderClass = Class.forName("nu.pattern.OpenCV", true, openCvClassLoader);
            Method loadLocally = loaderClass.getMethod("loadLocally");
            loadLocally.invoke(null);
            libraryLoaded = true;
            lastError = null;
            return true;
        } catch (ClassNotFoundException ignored) {
            // fall through to System.loadLibrary path
        } catch (ReflectiveOperationException | SecurityException | MalformedURLException e) {
            lastError = "OpenCV bundled loader failed: " + e.getMessage();
            System.err.println(lastError);
            return false;
        } catch (LinkageError e) {
            lastError = "OpenCV bundled native library unavailable: " + e.getMessage();
            System.err.println(lastError);
            return false;
        }

        try {
            Class<?> coreClass = loadClass("org.opencv.core.Core");
            String nativeName = (String) coreClass.getField("NATIVE_LIBRARY_NAME").get(null);
            System.loadLibrary(nativeName);
            libraryLoaded = true;
            lastError = null;
        } catch (ReflectiveOperationException | SecurityException | MalformedURLException e) {
            lastError = "OpenCV classes not available: " + e.getMessage();
            System.err.println(lastError);
        } catch (UnsatisfiedLinkError e) {
            lastError = "Failed to load OpenCV native library: " + e.getMessage();
            System.err.println(lastError);
        }
        return libraryLoaded;
    }

    private static Class<?> loadClass(String name) throws ClassNotFoundException, MalformedURLException {
        return Class.forName(name, true, getClassLoader());
    }

    private static ClassLoader getClassLoader() throws MalformedURLException {
        try {
            Class.forName("org.opencv.core.Core", false, openCvClassLoader);
            return openCvClassLoader;
        } catch (ClassNotFoundException ignored) {
            Path jarPath = Path.of(OPENCV_JAR_PATH);
            if (!Files.exists(jarPath)) {
                lastError = "OpenCV jar not found at " + OPENCV_JAR_PATH;
                return openCvClassLoader;
            }
            URL jarUrl = jarPath.toUri().toURL();
            openCvClassLoader = new URLClassLoader(new URL[]{jarUrl}, OpenCvBridge.class.getClassLoader());
            return openCvClassLoader;
        }
    }
}
