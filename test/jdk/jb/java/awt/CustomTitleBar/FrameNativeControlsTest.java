import com.jetbrains.JBR;
import util.Rect;
import util.ScreenShotHelpers;
import util.Task;
import util.TestUtils;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;
import java.awt.image.BufferedImage;
import java.util.List;

/*
 * @test
 * @summary Detect and check behavior of clicking to native controls
 * @requires (os.family == "windows" | os.family == "mac")
 * @run shell run.sh
 * @run main FrameNativeControlsTest
 */
public class FrameNativeControlsTest {

    public static void main(String... args) {
        boolean status = frameNativeControlsClicks.run(TestUtils::createFrameWithCustomTitleBar);

        if (!status) {
            throw new RuntimeException("FrameNativeControlsTest FAILED");
        }
    }

    private static final Task frameNativeControlsClicks = new Task("Frame native controls clicks") {
        private boolean closingActionCalled;
        private boolean iconifyingActionCalled;
        private boolean maximizingActionDetected;
        private boolean deiconifyindActionDetected;

        private final WindowListener windowListener = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closingActionCalled = true;
            }

            @Override
            public void windowIconified(WindowEvent e) {
                iconifyingActionCalled = true;
                window.setVisible(true);
            }
        };

        private final WindowStateListener windowStateListener = new WindowAdapter() {
            @Override
            public void windowStateChanged(WindowEvent e) {
                System.out.println("change " + e.getOldState() + " -> " + e.getNewState());
                if (e.getNewState() == 6) {
                    maximizingActionDetected = true;
                }
                if (e.getOldState() == 1 && e.getNewState() == 0) {
                    deiconifyindActionDetected = true;
                }
            }
        };

        @Override
        public void prepareTitleBar() {
            titleBar = JBR.getWindowDecorations().createCustomTitleBar();
            titleBar.setHeight(TestUtils.TITLE_BAR_HEIGHT);
        }

        @Override
        protected void init() {
            closingActionCalled = false;
            iconifyingActionCalled = false;
            maximizingActionDetected = false;
            deiconifyindActionDetected = false;
        }

        @Override
        protected void cleanup() {
            window.removeWindowListener(windowListener);
            window.removeWindowStateListener(windowStateListener);
        }

        @Override
        protected void customizeWindow() {
            window.addWindowListener(windowListener);
            window.addWindowStateListener(windowStateListener);
        }

        @Override
        public void test() throws Exception {
            Robot robot = new Robot();
            robot.delay(1000);

            BufferedImage image = ScreenShotHelpers.takeScreenshot(window);
            List<Rect> foundControls = ScreenShotHelpers.detectControls(image, (int) titleBar.getHeight(), (int) titleBar.getLeftInset(), (int) titleBar.getRightInset());

            if (foundControls.size() == 0) {
                passed = false;
                System.out.println("Error: no controls found");
            }

            foundControls.forEach(control -> {
                System.out.println("Using control: " + control);
                int x = window.getLocationOnScreen().x + control.getX1() + (control.getX2() - control.getX1()) / 2;
                int y = window.getLocationOnScreen().y + control.getY1() + (control.getY2() - control.getY1()) / 2;
                System.out.println("Click to (" + x + ", " + y + ")");

                int screenX = window.getBounds().x;
                int screenY = window.getBounds().y;
                int h = window.getBounds().height;
                int w = window.getBounds().width;

                robot.delay(500);
                robot.mouseMove(x, y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                robot.delay(1500);
                window.setBounds(screenX, screenY, w, h);
                window.setVisible(true);
                robot.delay(1500);
            });

            if (!maximizingActionDetected) {
                passed = false;
                System.out.println("Error: maximizing action was not detected");
            }

            if (!closingActionCalled) {
                passed = false;
                System.out.println("Error: closing action was not detected");
            }

            if (!iconifyingActionCalled) {
                passed = false;
                System.out.println("Error: iconifying action was not detected");
            }
            if (!deiconifyindActionDetected) {
                passed = false;
                System.out.println("Error: deiconifying action was not detected");
            }
        }
    };

}
