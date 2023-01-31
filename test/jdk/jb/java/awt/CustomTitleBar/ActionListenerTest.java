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

import com.jetbrains.JBR;
import util.CommonAPISuite;
import util.Task;
import util.TestUtils;

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Robot;
import java.awt.event.InputEvent;

/*
 * @test
 * @summary Regression test for JET-5124
 * @requires (os.family == "windows" | os.family == "mac")
 * @run shell run.sh
 * @run main ActionListenerTest
 */
public class ActionListenerTest {

    public static void main(String... args) {
        boolean status = CommonAPISuite.runTestSuite(TestUtils.getWindowCreationFunctions(), actionListener);

        if (!status) {
            throw new RuntimeException("ActionListenerTest FAILED");
        }
    }

    private static final Task actionListener = new Task("Using of action listener") {

        private Button button;

        @Override
        public void prepareTitleBar() {
            titleBar = JBR.getWindowDecorations().createCustomTitleBar();
            titleBar.setHeight(TestUtils.TITLE_BAR_HEIGHT);
        }

        @Override
        public void customizeWindow() {
            button = new Button();
            button.setBounds(200, 20, 50, 50);
            button.addActionListener(a -> {
                System.out.println("Action listener got event");
            });
            window.add(button);
        }

        @Override
        public void test() throws AWTException {
            final int initialHeight = window.getHeight();
            final int initialWidth = window.getWidth();

            Robot robot = new Robot();
            robot.delay(1000);
            robot.mouseMove(button.getBounds().x + button.getBounds().width / 2, button.getBounds().y + button.getBounds().height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(1000);

            if (window.getHeight() != initialHeight || window.getWidth() != initialWidth) {
                passed = false;
                System.out.println("Adding of action listener should block native title bar behavior");
            }
        }
    };

}
