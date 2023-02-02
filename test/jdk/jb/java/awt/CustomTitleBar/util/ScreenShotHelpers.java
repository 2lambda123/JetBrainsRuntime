package util;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class ScreenShotHelpers {

    public static BufferedImage takeScreenshot(Window window) throws AWTException {
        Robot robot = new Robot();

        final BufferedImage screenShot = robot.createScreenCapture(
                new Rectangle(window.getLocationOnScreen().x, window.getLocationOnScreen().y,
                        window.getWidth(), window.getHeight()));
        return screenShot;
    }

    public static void storeScreenshot(String namePrefix, BufferedImage image) throws IOException {
        final String fileName = String.format("%s-%s.png", namePrefix, UUID.randomUUID());
        ImageIO.write(image, "png", new File(fileName));
    }

    public static RectCoordinates findRectangleTitleBar(BufferedImage image, int titleBarHeight) {
        int centerX = image.getWidth() / 2;
        int centerY = titleBarHeight / 2;

        final int color = image.getRGB(centerX, centerY);

        int startY = centerY;
        for (int y = centerY; y >= 0; y--) {
            if (image.getRGB(centerX, y) != color) {
                startY = y + 1;
                break;
            }
        }

        int endY = centerY;
        for (int y = centerY; y <= (int) TestUtils.TITLE_BAR_HEIGHT; y++) {
            if (image.getRGB(centerX, y) != color) {
                endY = y - 1;
                break;
            }
        }

        int startX = centerX;
        for (int x = centerX; x >= 0; x--) {
            if (image.getRGB(x, startY) != color) {
                startX = x + 1;
                break;
            }
        }

        int endX = centerX;
        for (int x = centerX; x < image.getWidth(); x++) {
            if (image.getRGB(x, startY) != color) {
                endX = x - 1;
                break;
            }
        }

        return new RectCoordinates(startX, startY, endX, endY);
    }

    public static List<Rect> detectControls(BufferedImage image, int titleBarHeight, int leftInset, int rightInset) {
        RectCoordinates coords = ScreenShotHelpers.findRectangleTitleBar(image, titleBarHeight);

        Map<Color, Rect> map = new HashMap<>();

        System.out.println(coords);

        for (int x = coords.x1(); x <= coords.x2(); x++) {
            for (int y = coords.y1(); y <= coords.y2(); y++) {
                Color color = new Color(image.getRGB(x, y));
                Color adjustedColor = adjustColor(color);
                //int key = colorToInt(adjustedColor);
                Rect rect = map.getOrDefault(adjustedColor, new Rect(adjustedColor));
                rect.addPoint(x, y);
                map.put(adjustedColor, rect);
            }
        }

        int checkedHeight = coords.y2() - coords.y1() + 1;
        int checkedWidth = coords.x2() - coords.x1() + 1;
        int pixels = checkedWidth * checkedHeight;
        int nonCoveredAreaApprox = pixels - (leftInset * checkedHeight + rightInset * checkedHeight);

        List<Rect> rects = map.values().stream().filter(v -> v.getPixelCount() < nonCoveredAreaApprox).toList();
        List<Rect> foundControls = groupRects(rects);

        return foundControls;
    }

    private static List<Rect> groupRects(List<Rect> rects) {
        rects.forEach(System.out::println);
        List<Rect> found = new ArrayList<>();

        List<Rect> items = new ArrayList<>(rects);
        while (!items.isEmpty()) {
            AtomicReference<Rect> rect = new AtomicReference<>(items.remove(0));

            List<Rect> restItems = new ArrayList<>();
            items.forEach(item -> {
                Rect intersected = intersect(rect.get(), item);
                if (intersected != null) {
                    rect.set(intersected);
                } else {
                    restItems.add(item);
                }
            });
            found.add(rect.get());
            items = restItems;
        }

        return found;
    }

    private static Color adjustColor(Color color) {
        int r = adjustValue(color.getRed());
        int g = adjustValue(color.getGreen());
        int b = adjustValue(color.getBlue());
        return new Color(r, g, b);
    }

    private static int adjustValue(int value) {
        final int round = 64;
        int div = (value + 1) / round;
        int mod = (value + 1) % round;
        int result = div > 0 ? round * div - 1 : 0;
        if (mod > 32) result += round;
        return result;
    }

    private static Rect intersect(Rect r1, Rect r2) {
        int x1 = -1, x2 = -1, y1 = -1, y2 = -1;
        if (r1.getX1() <= r2.getX1() && r2.getX1() <= r1.getX2()) {
            x1 = r1.getX1();
            x2 = Math.max(r2.getX2(), r1.getX2());
        }
        if (r2.getX1() <= r1.getX1() && r1.getX1() <= r2.getX2()) {
            x1 = r2.getX1();
            x2 = Math.max(r2.getX2(), r1.getX2());
        }

        if (r1.getY1() < r2.getY1() && r2.getY1() <= r2.getY1()) {
            y1 = r1.getY1();
            y2 = Math.max(r1.getY2(), r2.getY2());
        }
        if (r2.getY1() <= r1.getY1() && r1.getY1() <= r2.getY2()) {
            y1 = r2.getY1();
            y2 = Math.max(r1.getY2(), r2.getY2());
        }
        if (x1 == -1 || x2 == -1 || y1 == -1 || y2 == -1) {
            return null;
        }

        Color commonColor = r1.getPixelCount() > r2.getPixelCount() ? r1.getCommonColor() : r2.getCommonColor();

        return new Rect(x1, y1, x2, y2, r1.getPixelCount() + r2.getPixelCount(), commonColor);
    }


}
