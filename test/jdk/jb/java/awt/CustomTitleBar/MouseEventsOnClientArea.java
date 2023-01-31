import com.jetbrains.JBR;
import util.CommonAPISuite;
import util.Task;
import util.TestUtils;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

/*
 * @test
 * @summary Regression test for JET-5124
 * @requires (os.family == "windows" | os.family == "mac")
 * @run shell run.sh
 * @run main MouseEventsOnClientArea
 */
public class MouseEventsOnClientArea {

    public static void main(String... args) {
        boolean status = CommonAPISuite.runTestSuite(TestUtils.getWindowCreationFunctions(), mouseClicks);

        if (!status) {
            throw new RuntimeException("MouseEventsOnClientArea FAILED");
        }
    }

    private static final Task mouseClicks = new Task("mouseClicks") {

        private static final List<Integer> BUTTON_MASKS = List.of(
                InputEvent.BUTTON1_DOWN_MASK,
                InputEvent.BUTTON2_DOWN_MASK,
                InputEvent.BUTTON3_DOWN_MASK
        );
        private static final int PANEL_WIDTH = 400;
        private static final int PANEL_HEIGHT = (int) TestUtils.TITLE_BAR_HEIGHT;

        private final boolean[] buttonsPressed = new boolean[BUTTON_MASKS.size()];
        private final boolean[] buttonsReleased = new boolean[BUTTON_MASKS.size()];
        private final boolean[] buttonsClicked = new boolean[BUTTON_MASKS.size()];
        private boolean mouseEntered;
        private boolean mouseExited;

        private Panel panel;



        @Override
        protected void cleanup() {
            Arrays.fill(buttonsPressed, false);
            Arrays.fill(buttonsReleased, false);
            Arrays.fill(buttonsClicked, false);
            mouseEntered = false;
            mouseExited = false;
        }

        @Override
        public void prepareTitleBar() {
            titleBar = JBR.getWindowDecorations().createCustomTitleBar();
            titleBar.setHeight(TestUtils.TITLE_BAR_HEIGHT);
        }

        @Override
        protected void customizeWindow() {
            panel = new Panel() {
                @Override
                public void paint(Graphics g) {
                    Rectangle r = g.getClipBounds();
                    g.setColor(Color.CYAN);
                    g.fillRect(r.x, r.y, PANEL_WIDTH, PANEL_HEIGHT);
                    super.paint(g);
                }
            };
            panel.setBounds(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
            panel.setSize(PANEL_WIDTH, PANEL_HEIGHT);
            panel.addMouseListener(new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() >= 1 && e.getButton() <= 3) {
                        buttonsClicked[e.getButton() - 1] = true;
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() >= 1 && e.getButton() <= 3) {
                        buttonsPressed[e.getButton() - 1] = true;
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.getButton() >= 1 && e.getButton() <= 3) {
                        buttonsReleased[e.getButton() - 1] = true;
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    mouseEntered = true;
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    mouseExited = true;
                }
            });
            window.add(panel);
        }

        @Override
        public void test() throws AWTException {
            Robot robot = new Robot();

            BUTTON_MASKS.forEach(mask -> {
                robot.delay(500);

                robot.mouseMove(panel.getLocationOnScreen().x + panel.getWidth() / 2,
                        panel.getLocationOnScreen().y + panel.getHeight() / 2);
                robot.mousePress(mask);
                robot.mouseRelease(mask);

                robot.delay(500);
            });

            robot.delay(100);
            robot.mouseMove(panel.getLocationOnScreen().x + panel.getWidth() / 2,
                    panel.getLocationOnScreen().y + panel.getHeight() / 2);
            robot.delay(100);
            robot.mouseMove(panel.getLocationOnScreen().x + panel.getWidth() + 10,
                    panel.getLocationOnScreen().y + panel.getWidth() + 10);

            for (int i = 0; i < BUTTON_MASKS.size(); i++) {
                passed = passed && buttonsPressed[i] && buttonsReleased[i] && buttonsClicked[i];
            }
        }
    };

}
