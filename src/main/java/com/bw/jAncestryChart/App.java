package com.bw.jAncestryChart;

import com.bw.jAncestryChart.ui.MainWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point: opens the window, or renders a PNG when {@code --export} is given. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        LaunchOptions options;
        try {
            options = LaunchOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.print(LaunchOptions.USAGE);
            System.exit(2);
            return;
        }
        if (options.help()) {
            System.out.print(LaunchOptions.USAGE);
            return;
        }
        if (options.export() != null) {
            System.setProperty("java.awt.headless", "true");
            System.exit(CommandLineExport.run(options));
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                // keep the default look and feel
            }
            new MainWindow(options).setVisible(true);
        });
    }
}
