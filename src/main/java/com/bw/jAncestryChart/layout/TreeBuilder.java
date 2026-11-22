package com.bw.jAncestryChart.layout;

import com.bw.jAncestryChart.model.Family;
import com.bw.jAncestryChart.model.Person;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Collects the people of an ancestor or descendant chart into {@link BranchNode}s. */
public final class TreeBuilder {

    /**
     * @param root           the person the chart is about
     * @param mode           which direction to grow
     * @param maxGenerations how many generations beyond the root to include (0 = root only)
     */
    public BranchNode build(Person root, TreeMode mode, int maxGenerations) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(mode, "mode");
        BranchNode node = new BranchNode(root, 0, null);
        // the root person always shows their partners; further out only descendant charts do
        addPartners(node);
        Set<Person> path = new HashSet<>();
        path.add(root);
        grow(node, mode, Math.max(0, maxGenerations), path);
        computeSizes(node);
        return node;
    }

    private static void grow(BranchNode node, TreeMode mode, int maxGenerations, Set<Person> path) {
        if (mode == TreeMode.ANCESTORS) {
            growAncestors(node, maxGenerations, path);
        } else {
            growDescendants(node, maxGenerations, path);
        }
    }

    private static void growAncestors(BranchNode node, int maxGenerations, Set<Person> path) {
        List<Person> parents = node.person().parents();
        if (parents.isEmpty()) {
            return;
        }
        if (node.generation() >= maxGenerations) {
            node.truncated = true;
            return;
        }
        for (Person p : parents) {
            if (!path.add(p)) {
                continue;  // a person who is their own ancestor: broken data, do not loop forever
            }
            BranchNode parent = new BranchNode(p, node.generation() + 1, node);
            node.outer.add(parent);
            growAncestors(parent, maxGenerations, path);
            path.remove(p);
        }
        if (node.outer.size() == 2) {
            Family family = node.person().familiesAsChild().get(0);
            if (family.husband() != null && family.wife() != null) {
                node.parentFamily = family;
            }
        }
    }

    /** Children are added family by family, so that the children of one couple stay together. */
    private static void growDescendants(BranchNode node, int maxGenerations, Set<Person> path) {
        if (node.person().children().isEmpty()) {
            return;
        }
        if (node.generation() >= maxGenerations) {
            node.truncated = true;
            return;
        }
        Set<Person> added = new HashSet<>();
        for (Family family : node.person().familiesAsSpouse()) {
            for (Person c : family.children()) {
                if (c == node.person() || path.contains(c) || !added.add(c)) {
                    continue;
                }
                BranchNode child = new BranchNode(c, node.generation() + 1, node);
                child.parentFamily = family;
                addPartners(child);
                node.outer.add(child);
                path.add(c);
                growDescendants(child, maxGenerations, path);
                path.remove(c);
            }
        }
    }

    private static void addPartners(BranchNode node) {
        Set<Person> seen = new HashSet<>();
        for (Family family : node.person().familiesAsSpouse()) {
            Person partner = family.partnerOf(node.person());
            if (partner != null && seen.add(partner)) {
                node.partners.add(new BranchNode(partner, node.generation(), node, family));
            }
        }
    }

    private static void computeSizes(BranchNode node) {
        int size = 1;
        for (BranchNode c : node.outer) {
            computeSizes(c);
            size += c.size;
        }
        node.size = size;
    }
}
