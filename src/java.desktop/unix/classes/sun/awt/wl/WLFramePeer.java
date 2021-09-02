package sun.awt.wl;

import sun.java2d.pipe.Region;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.BufferCapabilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Insets;
import java.awt.MenuBar;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.PaintEvent;
import java.awt.image.ColorModel;
import java.awt.image.VolatileImage;
import java.awt.peer.ComponentPeer;
import java.awt.peer.ContainerPeer;
import java.awt.peer.FramePeer;

public class WLFramePeer implements FramePeer {
    private final Frame target;

    public WLFramePeer(Frame target) {
        this.target = target;
    }

    @Override
    public boolean isObscured() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canDetermineObscurity() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setVisible(boolean v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEnabled(boolean e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void paint(Graphics g) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void print(Graphics g) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBounds(int x, int y, int width, int height, int op) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void handleEvent(AWTEvent e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void coalescePaintEvent(PaintEvent e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Point getLocationOnScreen() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Dimension getPreferredSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Dimension getMinimumSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ColorModel getColorModel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Graphics getGraphics() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FontMetrics getFontMetrics(Font font) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dispose() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setForeground(Color c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBackground(Color c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFont(Font f) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCursorImmediately() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean requestFocus(Component lightweightChild, boolean temporary, boolean focusedWindowChangeAllowed, long time, FocusEvent.Cause cause) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFocusable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Image createImage(int width, int height) {
        throw new UnsupportedOperationException();
    }

    @Override
    public VolatileImage createVolatileImage(int width, int height) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GraphicsConfiguration getGraphicsConfiguration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean handlesWheelScrolling() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createBuffers(int numBuffers, BufferCapabilities caps) throws AWTException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Image getBackBuffer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void flip(int x1, int y1, int x2, int y2, BufferCapabilities.FlipContents flipAction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void destroyBuffers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reparent(ContainerPeer newContainer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReparentSupported() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void layout() {
    }

    @Override
    public void applyShape(Region shape) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setZOrder(ComponentPeer above) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean updateGraphicsData(GraphicsConfiguration gc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Insets getInsets() {
        return new Insets(0, 0, 0, 0);
    }

    @Override
    public void beginValidate() {
    }

    @Override
    public void endValidate() {
    }

    @Override
    public void beginLayout() {
    }

    @Override
    public void endLayout() {
    }

    @Override
    public void setTitle(String title) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMenuBar(MenuBar mb) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setResizable(boolean resizeable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setState(int state) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setMaximizedBounds(Rectangle bounds) {
    }

    @Override
    public void setBoundsPrivate(int x, int y, int width, int height) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Rectangle getBoundsPrivate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void emulateActivation(boolean activate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void toFront() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void toBack() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAlwaysOnTopState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateFocusableWindowState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setModalBlocked(Dialog blocker, boolean blocked) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateMinimumSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateIconImages() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOpacity(float opacity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOpaque(boolean isOpaque) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateWindow() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void repositionSecurityWarning() {
        throw new UnsupportedOperationException();
    }
}
