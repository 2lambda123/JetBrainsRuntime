/*
 * Copyright 2000-2023 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package util;

import com.jetbrains.JBR;
import com.jetbrains.WindowDecorations;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.List;
import java.util.function.Function;

public class TestUtils {

    public static final float TITLE_BAR_HEIGHT = 100;
    public static final Color TITLE_BAR_COLOR = Color.BLUE;

    public static final int DEFAULT_LOCATION_X = 100;
    public static final int DEFAULT_LOCATION_Y = 100;
    private static final int DEFAULT_WIDTH = 1200;
    private static final int DEFAULT_HEIGHT = 600;


    private static final List<Function<WindowDecorations.CustomTitleBar, Window>> windowCreationFunctions = List.of(
            TestUtils::createJFrameWithCustomTitleBar,
            TestUtils::createFrameWithCustomTitleBar,
            TestUtils::createJDialogWithCustomTitleBar,
            TestUtils::createDialogWithCustomTitleBar
    );

    public static boolean checkTitleBarHeight(WindowDecorations.CustomTitleBar titleBar, float expected) {
        if (titleBar.getHeight() != expected) {
            System.out.printf(String.format("Wrong title bar height. Actual = %f, expected = %d\n", titleBar.getHeight(), expected));
            return false;
        }
        return true;
    }

    public static boolean checkFrameInsets(Window window) {
        Insets insets = window.getInsets();
        if (!(insets.top == 0 && insets.right == 0 && insets.left == 0 && insets.bottom == 0)) {
            System.out.println("Frame insets must be zero, but got " + insets);
            return false;
        }
        return true;
    }

    public static List<Function<WindowDecorations.CustomTitleBar, Window>> getWindowCreationFunctions() {
        return windowCreationFunctions;
    }

    public static Frame createFrameWithCustomTitleBar(WindowDecorations.CustomTitleBar titleBar) {
        Frame frame = new Frame(){
            @Override
            public void paint(Graphics g) {
                Rectangle r = g.getClipBounds();
                g.setColor(TITLE_BAR_COLOR);
                g.fillRect(r.x, r.y, r.width, (int) TITLE_BAR_HEIGHT);
            }
        };
        frame.setName("Frame");

        frame.setTitle("Frame");
        frame.setBounds(DEFAULT_LOCATION_X, DEFAULT_LOCATION_Y, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        JBR.getWindowDecorations().setCustomTitleBar(frame, titleBar);

        return frame;
    }

    public static JFrame createJFrameWithCustomTitleBar(WindowDecorations.CustomTitleBar titleBar) {
        JFrame frame = new JFrame();
        frame.setContentPane(new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Rectangle r = g.getClipBounds();
                g.setColor(Color.BLUE);
                g.fillRect(r.x, r.y, r.width, 100);
            }
        });
        frame.setName("JFrame");

        frame.setTitle("JFrame");
        frame.setBounds(DEFAULT_LOCATION_X, DEFAULT_LOCATION_Y, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        JBR.getWindowDecorations().setCustomTitleBar(frame, titleBar);

        return frame;
    }

    public static Dialog createDialogWithCustomTitleBar(WindowDecorations.CustomTitleBar titleBar) {
        Dialog dialog = new Dialog((Frame) null){
            @Override
            public void paint(Graphics g) {
                Rectangle r = g.getClipBounds();
                g.setColor(TITLE_BAR_COLOR);
                g.fillRect(r.x, r.y, r.width, (int) TITLE_BAR_HEIGHT);
            }
        };
        dialog.setName("Dialog");

        dialog.setTitle("Dialog");
        dialog.setBounds(DEFAULT_LOCATION_X, DEFAULT_LOCATION_Y, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        JBR.getWindowDecorations().setCustomTitleBar(dialog, titleBar);

        return dialog;
    }

    public static JDialog createJDialogWithCustomTitleBar(WindowDecorations.CustomTitleBar titleBar) {
        JDialog dialog = new JDialog();
        dialog.setContentPane(new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Rectangle r = g.getClipBounds();
                g.setColor(TITLE_BAR_COLOR);
                g.fillRect(r.x, r.y, r.width, (int) TITLE_BAR_HEIGHT);
            }
        });
        dialog.setName("JDialog");

        dialog.setTitle("JDialog");
        dialog.setBounds(DEFAULT_LOCATION_X, DEFAULT_LOCATION_Y, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        JBR.getWindowDecorations().setCustomTitleBar(dialog, titleBar);

        return dialog;
    }

}
