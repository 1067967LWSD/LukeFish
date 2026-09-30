package chess;

import java.awt.Font;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.FontUIResource;

import chess.ui.ChessFrame;

/** Start here in Eclipse: Run As -> Java Application, or Debug As -> Java Application. */
public final class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            LOG.log(Level.SEVERE, "Unexpected failure on " + thread.getName(), error);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "An unexpected error occurred.\n" + error + "\nSee the Console for details.",
                    "LukeFish error", JOptionPane.ERROR_MESSAGE));
        });
        configureAppearance();
        // Like a browser's event loop, Swing has a designated UI thread.
        // invokeLater schedules work there instead of touching widgets here.
        SwingUtilities.invokeLater(() -> {
            ChessFrame frame = new ChessFrame();
            frame.setVisible(true);
        });
    }

    /** Shared by the application and its desktop smoke check. */
    public static void configureAppearance() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException exception) {
            LOG.log(Level.WARNING, "Native appearance unavailable; using Swing's built-in appearance.", exception);
        }
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof FontUIResource font) {
                UIManager.put(key, new FontUIResource(font.deriveFont(Font.PLAIN, 14f)));
            }
        }
    }
}
