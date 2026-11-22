package com.bw.jAncestryChart;

import com.bw.jAncestryChart.gedcom.GedcomException;
import com.bw.jAncestryChart.gedcom.GedcomReader;
import com.bw.jAncestryChart.layout.BranchNode;
import com.bw.jAncestryChart.layout.GenerationLayout;
import com.bw.jAncestryChart.layout.TreeBuilder;
import com.bw.jAncestryChart.layout.TreeLayout;
import com.bw.jAncestryChart.model.FamilyTree;
import com.bw.jAncestryChart.model.GedcomModelBuilder;
import com.bw.jAncestryChart.model.Person;
import com.bw.jAncestryChart.render.PortraitCache;
import com.bw.jAncestryChart.render.TreeExporter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/** {@code --export}: renders a chart to PNG without opening a window. */
final class CommandLineExport {

    private CommandLineExport() {
    }

    static int run(LaunchOptions options) {
        try {
            FamilyTree tree = new GedcomModelBuilder().build(new GedcomReader().read(options.file()));
            List<String> warnings = tree.warnings();
            warnings.stream().limit(20).forEach(w -> System.err.println("Warning: " + w));
            if (warnings.size() > 20) {
                System.err.println("... and " + (warnings.size() - 20) + " more warnings");
            }
            Person root = options.root() == null ? tree.suggestRoot() : tree.findPerson(options.root()).orElse(null);
            if (root == null) {
                System.err.println(options.root() == null
                        ? "Error: the file contains no people."
                        : "Error: no person matches \"" + options.root() + "\".");
                return 1;
            }
            BranchNode nodes = new TreeBuilder().build(root, options.mode(), options.generations());
            GenerationLayout.Settings settings = new GenerationLayout.Settings(options.portraits(),
                    options.marriages(), options.spacing(), options.compactFrom(), options.straightLines(),
                    options.coupleGap());
            TreeLayout layout = new GenerationLayout(settings).layout(nodes, options.mode());
            BufferedImage image = TreeExporter.writePng(layout, new PortraitCache(), options.scale(),
                    options.portraits(), options.export());
            System.out.printf("Wrote %s (%d x %d px): %s – %d people in %d generations%n", options.export(),
                    image.getWidth(), image.getHeight(), layout.title(), layout.personCount(),
                    layout.maxGeneration() + 1);
            return 0;
        } catch (IOException | GedcomException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
