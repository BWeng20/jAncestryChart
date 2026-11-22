package com.bw.jAncestryChart.ui;

import com.bw.jAncestryChart.layout.BranchNode;
import com.bw.jAncestryChart.layout.TreeLayout;
import com.bw.jAncestryChart.model.Family;
import com.bw.jAncestryChart.model.LifeEvent;
import com.bw.jAncestryChart.model.Person;
import com.bw.jAncestryChart.render.TreeRenderer;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.function.Consumer;

/**
 * The drawing surface: shows a {@link TreeLayout}, lets the user pan (drag), zoom (wheel, +/-),
 * fit (double-click, 0) and pick a person (click).
 */
public final class TreeCanvas extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final double MIN_SCALE = 0.02;
    private static final double MAX_SCALE = 10;

    private final transient TreeRenderer renderer;
    private final AffineTransform view = new AffineTransform();
    private transient TreeLayout layout;
    private transient BranchNode hover;
    private transient Consumer<Person> personClicked = p -> { };
    private boolean portraits = true;
    private boolean userMoved;
    private Point dragStart;
    private AffineTransform dragView;
    private boolean dragged;

    public TreeCanvas(TreeRenderer renderer) {
        this.renderer = renderer;
        setOpaque(true);
        setFocusable(true);
        setPreferredSize(new Dimension(1100, 760));
        ToolTipManager.sharedInstance().registerComponent(this);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                dragStart = e.getPoint();
                dragView = new AffineTransform(view);
                dragged = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) {
                    return;
                }
                int dx = e.getX() - dragStart.x;
                int dy = e.getY() - dragStart.y;
                if (!dragged && Math.hypot(dx, dy) < 4) {
                    return;
                }
                dragged = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                view.setTransform(dragView);
                view.preConcatenate(AffineTransform.getTranslateInstance(dx, dy));
                userMoved = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                updateHover(e.getPoint());
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (dragged || e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                BranchNode node = nodeAt(e.getPoint());
                if (node != null && e.getClickCount() == 1) {
                    personClicked.accept(node.person());
                } else if (node == null && e.getClickCount() == 2) {
                    fit();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getPoint());
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoom(Math.pow(1.15, -e.getPreciseWheelRotation()), e.getPoint());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!userMoved) {
                    fit();
                }
            }
        });
        bindKey("PLUS", "ADD", "EQUALS", () -> zoomCentered(1.25));
        bindKey("MINUS", "SUBTRACT", null, () -> zoomCentered(0.8));
        bindKey("0", "NUMPAD0", "HOME", this::fit);
        bindKey("LEFT", null, null, () -> pan(60, 0));
        bindKey("RIGHT", null, null, () -> pan(-60, 0));
        bindKey("UP", null, null, () -> pan(0, 60));
        bindKey("DOWN", null, null, () -> pan(0, -60));
    }

    /** Called with the person whose plaque was clicked. */
    public void setPersonClickHandler(Consumer<Person> handler) {
        personClicked = handler == null ? p -> { } : handler;
    }

    public void setPortraits(boolean portraits) {
        this.portraits = portraits;
        repaint();
    }

    public TreeLayout treeLayout() {
        return layout;
    }

    /** Shows a new chart, fitted to the window. */
    public void showLayout(TreeLayout newLayout) {
        layout = newLayout;
        hover = null;
        userMoved = false;
        fit();
        repaint();
    }

    public void fit() {
        if (layout == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        Rectangle2D b = layout.bounds();
        double scale = Math.min(getWidth() / b.getWidth(), getHeight() / b.getHeight()) * 0.97;
        view.setToIdentity();
        view.translate(getWidth() / 2.0, getHeight() / 2.0);
        view.scale(scale, scale);
        view.translate(-b.getCenterX(), -b.getCenterY());
        userMoved = false;
        repaint();
    }

    public void zoomCentered(double factor) {
        zoom(factor, new Point(getWidth() / 2, getHeight() / 2));
    }

    private void zoom(double factor, Point anchor) {
        double current = view.getScaleX();
        double target = Math.max(MIN_SCALE, Math.min(MAX_SCALE, current * factor));
        double f = target / current;
        AffineTransform z = new AffineTransform();
        z.translate(anchor.x, anchor.y);
        z.scale(f, f);
        z.translate(-anchor.x, -anchor.y);
        view.preConcatenate(z);
        userMoved = true;
        repaint();
    }

    private void pan(double dx, double dy) {
        view.preConcatenate(AffineTransform.getTranslateInstance(dx, dy));
        userMoved = true;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            TreeRenderer.applyQualityHints(g);
            renderer.paintBackground(g, getWidth(), getHeight());
            if (layout == null) {
                paintHint(g);
                return;
            }
            g.transform(view);
            renderer.paint(g, layout, new TreeRenderer.Options(portraits), hover);
        } finally {
            g.dispose();
        }
    }

    private void paintHint(Graphics2D g) {
        String text = "Open a GEDCOM 7 file (Ctrl+O) or drop it here";
        g.setFont(new Font(Font.SERIF, Font.ITALIC, 22));
        g.setColor(new Color(0x75695A));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        BranchNode node = nodeAt(e.getPoint());
        return node == null ? null : describe(node, layout.showsMarriages());
    }

    private void updateHover(Point p) {
        BranchNode node = nodeAt(p);
        if (node != hover) {
            hover = node;
            repaint();
        }
        if (dragStart == null) {
            setCursor(Cursor.getPredefinedCursor(node != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
    }

    private BranchNode nodeAt(Point p) {
        if (layout == null) {
            return null;
        }
        try {
            Point2D world = view.inverseTransform(p, null);
            return layout.nodeAt(world.getX(), world.getY());
        } catch (NoninvertibleTransformException e) {
            return null;
        }
    }

    static String describe(BranchNode node, boolean marriages) {
        Person p = node.person();
        StringBuilder sb = new StringBuilder("<html><b>").append(escape(p.fullName())).append("</b>");
        appendEvent(sb, p.birth());
        appendEvent(sb, p.death());
        if (p.isDeceased() && (p.death() == null || p.death().describe().isEmpty())) {
            sb.append("<br>Deceased");
        }
        if (!p.occupations().isEmpty()) {
            sb.append("<br>Occupation: ").append(escape(String.join(", ", p.occupations())));
        }
        for (Family f : p.familiesAsSpouse()) {
            Person partner = f.partnerOf(p);
            if (partner == null) {
                continue;
            }
            sb.append("<br>∞ ").append(escape(partner.fullName()));
            String married = f.marriage() == null ? "" : f.marriage().describe();
            if (!married.isEmpty() || f.isDivorced()) {
                sb.append(" (").append(escape(married));
                sb.append(f.isDivorced() ? (married.isEmpty() ? "divorced" : ", divorced") : "").append(')');
            }
        }
        Family parents = node.parentFamily();
        if (marriages && parents != null) {
            String married = parents.marriage() == null ? "" : parents.marriage().describe();
            sb.append("<br>Parents married").append(married.isEmpty() ? " (date unknown)" : ": " + escape(married));
            if (parents.isDivorced()) {
                sb.append(", later divorced");
            }
        }
        sb.append("<br><i>Click to show the chart of this person</i></html>");
        return sb.toString();
    }

    private static void appendEvent(StringBuilder sb, LifeEvent event) {
        if (event != null && !event.describe().isEmpty()) {
            sb.append("<br>").append(event.label()).append(": ").append(escape(event.describe()));
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void bindKey(String key, String alternative, String third, Runnable action) {
        String name = "action-" + key;
        for (String k : new String[] {key, alternative, third}) {
            if (k != null) {
                getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(k), name);
            }
        }
        getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }
}
