/**
 * src/CameraPreviewDialog.java
 *
 * Modal dialog that opens a live webcam preview and lets the user snap a photo.
 * Runs a background reader thread so the EDT stays responsive. On close, the capture
 * is released exactly once after the reader has stopped, this avoids the
 * MSMF "SourceReaderCB terminating async callback" warning that the previous
 * open/warmup/release retry loop in Main.java produced.
 *
 * @since 2026-04-23
 */
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;

public class CameraPreviewDialog extends JDialog {
    private static final int PREVIEW_W = 480;
    private static final int PREVIEW_H = 360;
    private static final long FRAME_INTERVAL_MS = 50L;
    private static final long OPEN_TIMEOUT_MS = 4000L;

    private final JLabel previewLabel = new JLabel("Starting camera…", SwingConstants.CENTER);
    private final JButton captureButton = new JButton("Capture");
    private final JButton cancelButton = new JButton("Cancel");

    private volatile boolean running = true;
    private volatile BufferedImage latestFrame;
    private BufferedImage capturedImage;
    private Thread readerThread;
    private OpenCvBridge bridge;

    public CameraPreviewDialog(Window owner) {
        super(owner, "Take a photo", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        previewLabel.setPreferredSize(new Dimension(PREVIEW_W, PREVIEW_H));
        previewLabel.setOpaque(true);
        previewLabel.setBackground(Color.BLACK);
        previewLabel.setForeground(Color.WHITE);

        JPanel buttons = new JPanel();
        buttons.add(captureButton);
        buttons.add(cancelButton);

        captureButton.setEnabled(false);
        captureButton.addActionListener(e -> {
            capturedImage = latestFrame;
            closeAndRelease();
        });
        cancelButton.addActionListener(e -> closeAndRelease());

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { closeAndRelease(); }
        });

        setLayout(new BorderLayout());
        add(previewLabel, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            startReader();
        }
        super.setVisible(visible);
    }

    public BufferedImage getCapturedImage() {
        return capturedImage;
    }

    private void startReader() {
        readerThread = new Thread(() -> {
            bridge = OpenCvBridge.open();
            if (bridge == null) {
                SwingUtilities.invokeLater(this::failAndClose);
                return;
            }

            long deadline = System.currentTimeMillis() + OPEN_TIMEOUT_MS;
            boolean gotFirstFrame = false;

            while (running) {
                BufferedImage frame = bridge.readFrame();
                if (frame != null) {
                    latestFrame = frame;
                    if (!gotFirstFrame) {
                        gotFirstFrame = true;
                        SwingUtilities.invokeLater(() -> captureButton.setEnabled(true));
                    }
                    Image scaled = frame.getScaledInstance(PREVIEW_W, PREVIEW_H, Image.SCALE_FAST);
                    SwingUtilities.invokeLater(() -> {
                        previewLabel.setIcon(new ImageIcon(scaled));
                        previewLabel.setText(null);
                    });
                } else if (!gotFirstFrame && System.currentTimeMillis() > deadline) {
                    SwingUtilities.invokeLater(this::failAndClose);
                    return;
                }

                try {
                    Thread.sleep(FRAME_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Camera-Preview-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void failAndClose() {
        running = false;
        dispose();
    }

    private void closeAndRelease() {
        running = false;
        if (readerThread != null) {
            try {
                readerThread.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (bridge != null) {
            bridge.release();
            bridge = null;
        }
        dispose();
    }
}
