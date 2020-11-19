// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @test
 * @summary manual test for JBR-2785
 * @author Artem.Semenov@jetbrains.com
 * @run main/manual AccessibleJTreeTest
 */

import javax.swing.*;
import java.awt.*;
import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AccessibleJTreeTest extends AccessibleComponentTest {

    @Override
    public CountDownLatch createCountDownLatch() {
        return new CountDownLatch(1);
    }

    public void createUI() {
        INSTRUCTIONS = "INSTRUCTIONS:\n"
                + "Check a11y of JTree in a simple Window.\n\n"
                + "Turn screen reader on, and Tab to the tree.\n"
                + "Press the arrow buttons to move through the tree.\n\n"
                + "If you can hear tree components tab further and press PASS, otherwise press FAIL.\n";

        String[] nodes = new String[]{"One nod", "Two nod"};
        String[][] leafs = new String[][]{{"leaf 1.1", "leaf 1.2", "leaf 1.3", "leaf 1.4"},
                {"leaf 2.1", "leaf 2.2", "leaf 2.3", "leaf 2.4"}};

        Hashtable<String, String[]> data = new Hashtable<String, String[]>();
        for (int i = 0; i < nodes.length; i++) {
            data.put(nodes[i], leafs[i]);
        }

        JTree tree = new JTree(data);
        tree.setRootVisible(true);

        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());
        JScrollPane scrollPane = new JScrollPane(tree);
        panel.add(scrollPane);
        panel.setFocusable(false);
        exceptionString = "AccessibleJTree test failed!";
        super.createUI(panel, "AccessibleJTreeTest");
    }

    public static void main(String[] args) throws Exception {
        AccessibleJTreeTest test = new AccessibleJTreeTest();
        countDownLatch = test.createCountDownLatch();
        SwingUtilities.invokeAndWait(test::createUI);
        countDownLatch.await(15, TimeUnit.MINUTES);
        if (!testResult) {
            throw new RuntimeException(exceptionString);
        }
    }
}
