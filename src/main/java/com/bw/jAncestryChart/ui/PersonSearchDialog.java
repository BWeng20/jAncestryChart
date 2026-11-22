package com.bw.jAncestryChart.ui;

import com.bw.jAncestryChart.model.FamilyTree;
import com.bw.jAncestryChart.model.Person;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;

/** A small "type to filter" dialog for choosing the person the chart is about. */
final class PersonSearchDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final transient FamilyTree tree;
    private final JTextField field = new JTextField();
    private final DefaultListModel<Person> model = new DefaultListModel<>();
    private final JList<Person> list = new JList<>(model);
    private transient Person chosen;

    private PersonSearchDialog(Window owner, FamilyTree tree, Person current) {
        super(owner, "Choose the person the chart is about", ModalityType.APPLICATION_MODAL);
        this.tree = tree;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                                                          boolean focus) {
                Person p = (Person) value;
                String name = p.surname().isEmpty() ? p.fullName() : p.surname() + ", " + p.givenNames();
                String text = "<html>" + escape(name) + " <font color='#7a6f5f'>" + escape(p.lifeSpan()) + "</font></html>";
                return super.getListCellRendererComponent(l, text, index, selected, focus);
            }
        });
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int i = list.getSelectedIndex();
                if (e.getKeyCode() == KeyEvent.VK_DOWN && i < model.size() - 1) {
                    select(i + 1);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP && i > 0) {
                    select(i - 1);
                    e.consume();
                }
            }
        });
        field.addActionListener(e -> accept());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    accept();
                }
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(field, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(380, 420));
        panel.add(scroll, BorderLayout.CENTER);
        setContentPane(panel);
        pack();
        setLocationRelativeTo(owner);

        filter();
        if (current != null) {
            int index = model.indexOf(current);
            if (index >= 0) {
                select(index);
            }
        }
    }

    /** Shows the dialog and returns the chosen person, if any. */
    static Optional<Person> choose(Window owner, FamilyTree tree, Person current) {
        PersonSearchDialog dialog = new PersonSearchDialog(owner, tree, current);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.chosen);
    }

    private void filter() {
        model.clear();
        tree.search(field.getText()).forEach(model::addElement);
        if (!model.isEmpty()) {
            select(0);
        }
    }

    private void select(int index) {
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }

    private void accept() {
        chosen = list.getSelectedValue();
        if (chosen != null) {
            dispose();
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
