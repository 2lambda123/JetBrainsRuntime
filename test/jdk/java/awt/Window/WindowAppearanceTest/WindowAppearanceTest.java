/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @key headful
 * @bug 8265445
 * @summary [macosx] window appearance test
 * @author Alexey Ushakov
 * @run main WindowAppearanceTest
 * @requires (os.family == "mac")
 */

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import javax.swing.*;

public class WindowAppearanceTest
{
    private static final int TD = 10;
    // Colors for unfocused/focused frames
    private static final Color [] darkSystemGrays = {
            new Color(58, 58, 60), new Color(40, 37, 48), new Color(96, 93, 99)};
    private static final Color [] lightSystemGrays = {
            new Color(242, 242, 247), new Color(230, 228, 232)};
    static WindowAppearanceTest theTest;
    private Robot robot;
    private JFrame frame;
    private JRootPane rootPane;

    private int DELAY = 1000;

    public WindowAppearanceTest() {
        try {
            robot = new Robot();
        } catch (AWTException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void performTest() {

        runSwing(() -> {
            frame = new JFrame("");
            frame.setBounds(100, 100, 300, 150);
            rootPane = frame.getRootPane();
            JComponent contentPane = (JComponent) frame.getContentPane();
            JPanel comp = new JPanel();
            contentPane.add(comp);
            comp.setBackground(Color.RED);
            frame.setVisible(true);
        });

        robot.delay(DELAY);
        runSwing(() -> rootPane.putClientProperty("apple.awt.windowTitleVisible", false));
        runSwing(() -> rootPane.putClientProperty("apple.awt.windowAppearance", "NSAppearanceNameVibrantDark"));
        robot.delay(DELAY);

        validateColor(darkSystemGrays);

        runSwing(() -> rootPane.putClientProperty("apple.awt.windowAppearance", "NSAppearanceNameVibrantLight"));
        robot.delay(DELAY);

        validateColor(lightSystemGrays);

        runSwing(() -> frame.dispose());

        frame = null;
        rootPane = null;
    }

    private Color getTestPixel(int x, int y) {
        Rectangle bounds = frame.getBounds();
        BufferedImage screenImage = robot.createScreenCapture(bounds);
        int rgb = screenImage.getRGB(x, y);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        Color c = new Color(red, green, blue);
        return c;
    }

    private void validateColor(Color[] colors) {
        for (int px = 140; px < 160; px++) {
            for (int py = 5; py < 20; py++) {
                Color c = getTestPixel(px, py);
                boolean invalid = true;
                for (Color color : colors) {
                    if (validateColor(c, color)) {
                        invalid = false;
                        break;
                    } else {
                        System.out.println(color + " does not pass");
                    }
                }
                if (invalid) {
                    throw new RuntimeException("Test failed. Incorrect color " +
                            c + " at (" + px + "," + py + ")");
                }
            }
        }
    }

    private boolean validateColor(Color c, Color expected) {
        return Math.abs(c.getRed() - expected.getRed()) <= TD &&
            Math.abs(c.getGreen() - expected.getGreen()) <= TD &&
            Math.abs(c.getBlue() - expected.getBlue()) <= TD;
    }

    public void dispose() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private static void runSwing(Runnable r) {
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (InterruptedException e) {
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        if (!System.getProperty("os.name").contains("OS X")) {
            System.out.println("This test is for MacOS only. Automatically passed on other platforms.");
            return;
        }

        try {
            runSwing(() -> theTest = new WindowAppearanceTest());
            theTest.performTest();
        } finally {
            if (theTest != null) {
                runSwing(() -> theTest.dispose());
            }
        }
    }
}
