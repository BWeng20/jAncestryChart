package com.bw.jAncestryChart.ui;

import com.bw.jtools.svg.SVGConverter;
import com.bw.jtools.ui.ShapeIcon;
import com.bw.jtools.ui.ShapeMultiResolutionImage;

import com.bw.jAncestryChart.LaunchOptions;
import com.bw.jAncestryChart.Log;
import com.bw.jAncestryChart.gedcom.GedcomReader;
import com.bw.jAncestryChart.layout.BranchNode;
import com.bw.jAncestryChart.layout.GenerationLayout;
import com.bw.jAncestryChart.layout.TreeBuilder;
import com.bw.jAncestryChart.layout.TreeLayout;
import com.bw.jAncestryChart.layout.TreeMode;
import com.bw.jAncestryChart.model.FamilyTree;
import com.bw.jAncestryChart.model.GedcomModelBuilder;
import com.bw.jAncestryChart.model.Person;
import com.bw.jAncestryChart.render.PortraitCache;
import com.bw.jAncestryChart.render.TreeExporter;
import com.bw.jAncestryChart.render.TreeRenderer;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** The application window: toolbars, chart canvas and status line. */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final double EXPORT_SCALE = 2.0;

    private final transient PortraitCache portraits = new PortraitCache();
    private final TreeCanvas canvas = new TreeCanvas(new TreeRenderer(portraits));
    private final JButton rootButton = new JButton("Choose person…");
    private final JComboBox<TreeMode> modeBox = new JComboBox<>(TreeMode.values());
    private final SpinnerNumberModel generationsModel = new SpinnerNumberModel(8, 1, 100, 1);
    private final SpinnerNumberModel compactModel = new SpinnerNumberModel(4, 1, 100, 1);
    private final SpinnerNumberModel coupleGapModel = new SpinnerNumberModel(12, 0, 100, 2);
    private final JSlider spacingSlider = new JSlider(percent(GenerationLayout.Settings.MIN_SPACING),
            percent(GenerationLayout.Settings.MAX_SPACING), 100);
    private final JCheckBox portraitsBox = new JCheckBox("Portraits", true);
    private final JCheckBox marriagesBox = new JCheckBox("Marriages", true);
    private final JCheckBox straightBox = new JCheckBox("Straight lines", true);
    private final JLabel status = new JLabel(" ");
    private final transient Action exportAction;
    private final transient Action findAction;

    private transient FamilyTree tree;
    private transient Person root;
    private transient Path currentFile;

    private static final Map<String, Icon> icons = new HashMap<>();

    /**
     * Gets a svg icon by name. The svg file must exist in root folder of resources.
     *
     * @param name The name (filename without extension)
     * @return the icon, null if not found.
     */
    public static Icon getIcon(String name) {
        synchronized (icons) {
            Icon i = icons.get(name);
            if (i == null) {
                try (InputStream is = MainWindow.class.getResourceAsStream("/" + name + ".svg")) {
                    ShapeIcon si = new ShapeIcon(SVGConverter.convert(is));
                    icons.put(name, si);
                    i = si;
                } catch (Exception e) {
                    Log.error("Error in getting icon '%s': %s", name, e.getMessage());
                }
            }
            return i;
        }
    }


    /** Sets the SVG application icon for the frame and, where supported, the task bar / dock. */
    private void setAppIcon() {
        try (InputStream is = MainWindow.class.getResourceAsStream("/appicon.svg")) {
            ShapeMultiResolutionImage image = new ShapeMultiResolutionImage(SVGConverter.convert(is));
            setIconImage(image);
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(image.getResolutionVariant(256, 256));
                }
            }
        } catch (Exception e) {
            Log.error("Error in setting application icon: %s", e.getMessage());
        }
    }

    public MainWindow(LaunchOptions options) {
        super("jFamilyTreeMaker");
        Log.info("Starting main window...");
        setAppIcon();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        modeBox.setSelectedItem(options.mode());
        generationsModel.setValue(options.generations());
        compactModel.setValue(options.compactFrom());
        coupleGapModel.setValue((int) Math.round(options.coupleGap()));
        spacingSlider.setValue(percent(options.spacing()));
        portraitsBox.setSelected(options.portraits());
        marriagesBox.setSelected(options.marriages());
        marriagesBox.setToolTipText("Show a marriage seal between the partners of each couple");
        straightBox.setSelected(options.straightLines());
        straightBox.setToolTipText("<html>Ancestors: parents stand right above their child and move apart where<br>"
                + "their own parents need the room. Off: married couples always touch and the lines bend.</html>");

        Action openAction = action("Open GEDCOM…", KeyEvent.VK_O, e -> chooseFile());
        exportAction = action("Export PNG…", KeyEvent.VK_E, e -> exportPng());
        findAction = action("Find person…", KeyEvent.VK_F, e -> findPerson());
        Action fitAction = action("Fit to window", KeyEvent.VK_0, e -> canvas.fit());
        exportAction.setEnabled(false);
        findAction.setEnabled(false);

        setJMenuBar(menuBar(openAction, fitAction));
        JPanel toolbars = new JPanel(new GridLayout(2, 1));
        toolbars.add(treeToolbar(openAction, fitAction));
        toolbars.add(appearanceToolbar());
        add(toolbars, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        add(status, BorderLayout.SOUTH);

        rootButton.setToolTipText("Choose the person the chart is about (Ctrl+F)");
        rootButton.addActionListener(e -> findPerson());
        modeBox.addActionListener(e -> rebuild());
        generationsModel.addChangeListener(e -> rebuild());
        compactModel.addChangeListener(e -> rebuild());
        coupleGapModel.addChangeListener(e -> rebuild());
        spacingSlider.addChangeListener(e -> {
            if (!spacingSlider.getValueIsAdjusting()) {
                rebuild();
            }
        });
        portraitsBox.addActionListener(e -> rebuild());
        marriagesBox.addActionListener(e -> rebuild());
        straightBox.addActionListener(e -> rebuild());
        canvas.setPersonClickHandler(this::setRoot);

        GedcomDropHandler drop = new GedcomDropHandler();
        setTransferHandler(drop);
        canvas.setTransferHandler(drop);

        setSize(1280, 860);
        setLocationRelativeTo(null);
        if (options.file() != null) {
            open(options.file(), options.root());
        } else {
            status.setText("Open a GEDCOM 7 file (Ctrl+O) or drop it onto the window.");
        }

        Log.info("Main window is up.");

    }

    // ------------------------------------------------------------------ building the UI

    private JMenuBar menuBar(Action openAction, Action fitAction) {
        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(openAction);
        file.add(exportAction);
        file.addSeparator();
        file.add(action("Exit", 0, e -> dispose()));
        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        view.add(findAction);
        view.add(fitAction);
        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(view);
        return bar;
    }

    private JToolBar treeToolbar(Action openAction, Action fitAction) {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(3, 6, 1, 6));
        bar.add(openAction);
        bar.addSeparator();
        bar.add(new JLabel("Chart of "));
        bar.add(rootButton);
        bar.addSeparator();
        bar.add(new JLabel("Show "));
        modeBox.setMaximumSize(modeBox.getPreferredSize());
        bar.add(modeBox);
        bar.addSeparator();
        bar.add(new JLabel("Generations "));
        JSpinner spinner = new JSpinner(generationsModel);
        spinner.setMaximumSize(new Dimension(64, 30));
        bar.add(spinner);
        bar.add(Box.createHorizontalGlue());
        bar.add(fitAction);
        bar.add(exportAction);
        return bar;
    }

    private JToolBar appearanceToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(1, 6, 3, 6));
        addSlider(bar, "Spacing", spacingSlider, "Distance between generations and between neighbours");
        bar.addSeparator();
        JLabel compactLabel = new JLabel("Compact from generation ");
        JSpinner compactSpinner = new JSpinner(compactModel);
        compactSpinner.setMaximumSize(new Dimension(64, 30));
        String compactTip = "From this generation on: narrow upright boxes";
        compactLabel.setToolTipText(compactTip);
        compactSpinner.setToolTipText(compactTip);
        bar.add(compactLabel);
        bar.add(compactSpinner);
        bar.addSeparator();
        JLabel coupleGapLabel = new JLabel("Couple gap ");
        JSpinner coupleGapSpinner = new JSpinner(coupleGapModel);
        coupleGapSpinner.setMaximumSize(new Dimension(64, 30));
        String coupleGapTip = "Gap in pixels between the wide boxes of a married couple (narrow boxes always touch)";
        coupleGapLabel.setToolTipText(coupleGapTip);
        coupleGapSpinner.setToolTipText(coupleGapTip);
        bar.add(coupleGapLabel);
        bar.add(coupleGapSpinner);
        bar.addSeparator();
        bar.add(portraitsBox);
        bar.add(marriagesBox);
        bar.add(straightBox);
        bar.add(Box.createHorizontalGlue());
        return bar;
    }

    /** A slider with a caption that shows its current value in percent. */
    private static void addSlider(JToolBar bar, String caption, JSlider slider, String tooltip) {
        JLabel label = new JLabel();
        Runnable update = () -> label.setText(caption + " " + slider.getValue() + "% ");
        update.run();
        label.setPreferredSize(new Dimension(label.getFontMetrics(label.getFont())
                .stringWidth(caption + " 500% ") + 4, label.getPreferredSize().height));
        label.setMaximumSize(label.getPreferredSize());
        slider.addChangeListener(e -> update.run());
        slider.setToolTipText(tooltip);
        label.setToolTipText(tooltip);
        slider.setMaximumSize(new Dimension(130, 30));
        slider.setPreferredSize(new Dimension(130, 30));
        bar.add(label);
        bar.add(slider);
    }

    private static int percent(double factor) {
        return (int) Math.round(factor * 100);
    }

    private static Action action(String name, int key, Consumer<ActionEvent> handler) {
        Action a = new AbstractAction(name) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                handler.accept(e);
            }
        };
        if (key != 0) {
            a.putValue(Action.ACCELERATOR_KEY,
                    KeyStroke.getKeyStroke(key, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        }
        return a;
    }

    // ------------------------------------------------------------------ behaviour

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser(currentFile == null ? null : currentFile.toAbsolutePath().getParent().toFile());
        chooser.setDialogTitle("Open GEDCOM file");
        chooser.setFileFilter(new FileNameExtensionFilter("GEDCOM files (*.ged)", "ged"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            open(chooser.getSelectedFile().toPath(), null);
        }
    }

    /** Reads a file in the background, then shows the tree of {@code rootQuery} (or a sensible default). */
    public void open(Path file, String rootQuery) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        status.setText("Reading " + file.getFileName() + " …");
        new SwingWorker<FamilyTree, Void>() {
            @Override
            protected FamilyTree doInBackground() throws Exception {
                FamilyTree loaded = new GedcomModelBuilder().build(new GedcomReader().read(file));
                portraits.clear();
                portraits.preload(loaded.persons());
                return loaded;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    FamilyTree loaded = get();
                    Person start = rootQuery == null ? null : loaded.findPerson(rootQuery).orElse(null);
                    if (start == null) {
                        start = loaded.suggestRoot();
                    }
                    if (start == null) {
                        showError("The file contains no people.");
                        return;
                    }
                    tree = loaded;
                    root = start;
                    currentFile = file;
                    setTitle("jFamilyTreeMaker – " + file.getFileName());
                    exportAction.setEnabled(true);
                    findAction.setEnabled(true);
                    rebuild();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    status.setText("Could not read " + file.getFileName());
                    showError("Could not read " + file.getFileName() + ":\n" + e.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void setRoot(Person person) {
        if (person != null && person != root) {
            root = person;
            rebuild();
        }
    }

    private void findPerson() {
        if (tree != null) {
            PersonSearchDialog.choose(this, tree, root).ifPresent(this::setRoot);
        }
    }

    private void rebuild() {
        if (tree == null || root == null) {
            return;
        }
        TreeMode mode = (TreeMode) modeBox.getSelectedItem();
        int generations = generationsModel.getNumber().intValue();
        GenerationLayout.Settings settings = new GenerationLayout.Settings(portraitsBox.isSelected(),
                marriagesBox.isSelected(), spacingSlider.getValue() / 100.0, compactModel.getNumber().intValue(),
                straightBox.isSelected(), coupleGapModel.getNumber().doubleValue());
        BranchNode nodes = new TreeBuilder().build(root, mode, generations);
        TreeLayout layout = new GenerationLayout(settings).layout(nodes, mode);
        canvas.setPortraits(portraitsBox.isSelected());
        canvas.showLayout(layout);
        rootButton.setText(root.fullName() + "  ▾");
        updateStatus(layout);
    }

    private void updateStatus(TreeLayout layout) {
        StringBuilder sb = new StringBuilder();
        sb.append(currentFile.getFileName());
        if (!tree.gedcomVersion().isEmpty()) {
            sb.append(" · GEDCOM ").append(tree.gedcomVersion());
        }
        sb.append(" · ").append(tree.persons().size()).append(" people, ")
                .append(tree.families().size()).append(" families · showing ")
                .append(layout.personCount()).append(" people in ")
                .append(layout.maxGeneration() + 1).append(" generations");
        List<String> warnings = tree.warnings();
        if (!warnings.isEmpty()) {
            sb.append(" · ").append(warnings.size()).append(warnings.size() == 1 ? " warning" : " warnings");
            StringBuilder tip = new StringBuilder("<html>");
            warnings.stream().limit(25).forEach(w -> tip.append(escape(w)).append("<br>"));
            if (warnings.size() > 25) {
                tip.append("… and ").append(warnings.size() - 25).append(" more");
            }
            status.setToolTipText(tip.append("</html>").toString());
        } else {
            status.setToolTipText(null);
        }
        status.setText(sb.toString());
    }

    private void exportPng() {
        TreeLayout layout = canvas.treeLayout();
        if (layout == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser(currentFile.toAbsolutePath().getParent().toFile());
        chooser.setDialogTitle("Export chart as PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG images (*.png)", "png"));
        chooser.setSelectedFile(new File(layout.title().replaceAll("[\\\\/:*?\"<>|]", "_") + ".png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase().endsWith(".png")) {
            target = target.resolveSibling(target.getFileName() + ".png");
        }
        if (Files.exists(target) && JOptionPane.showConfirmDialog(this, target.getFileName() + " exists. Replace it?",
                "Export", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        Path out = target;
        boolean showPortraits = portraitsBox.isSelected();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        status.setText("Exporting " + out.getFileName() + " …");
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws IOException {
                return TreeExporter.writePng(layout, portraits, EXPORT_SCALE, showPortraits, out);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    BufferedImage image = get();
                    status.setText("Saved " + out + " (" + image.getWidth() + " × " + image.getHeight() + " px)");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    status.setText("Export failed");
                    showError("Could not export the image:\n" + e.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "jFamilyTreeMaker", JOptionPane.ERROR_MESSAGE);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Accepts a GEDCOM file dropped onto the window. */
    private final class GedcomDropHandler extends TransferHandler {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                List<?> files = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (files.isEmpty() || !(files.get(0) instanceof File f)) {
                    return false;
                }
                open(f.toPath(), null);
                return true;
            } catch (UnsupportedFlavorException | IOException e) {
                return false;
            }
        }
    }
}
