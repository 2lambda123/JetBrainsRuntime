/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, JetBrains s.r.o.. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package sun.awt.wl;

import java.awt.Window;
import sun.awt.PaintEventDispatcher;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.MenuBar;
import java.awt.Rectangle;
import java.awt.event.PaintEvent;
import java.awt.event.WindowEvent;
import java.awt.peer.FramePeer;
import sun.java2d.wl.WLSurfaceData;
import sun.util.logging.PlatformLogger;

public class WLFramePeer extends WLComponentPeer implements FramePeer {
    private static final PlatformLogger log = PlatformLogger.getLogger("sun.awt.wl.WLFramePeer");
    private long nativePtr;

    static {
        initIDs();
    }

    public WLFramePeer(Frame target) {
        super(target);
        this.nativePtr = nativeCreateFrame();
    }

    private static native void initIDs();

    @Override
    public void setVisible(boolean v) {
        if (v) {
            nativeShowFrame(nativePtr, target.getWidth(), target.getHeight());
            ((WLSurfaceData)surfaceData).initSurface(this, background != null ? background.getRGB() : 0, target.getWidth(), target.getHeight());
            PaintEvent event = PaintEventDispatcher.getPaintEventDispatcher().
                    createPaintEvent(target, 0, 0, target.getWidth(), target.getHeight());
            if (event != null) {
                WLToolkit.postEvent(WLToolkit.targetToAppContext(event.getSource()), event);
            }
        } else {
            nativeHideFrame(nativePtr);
        }
    }


    @Override
    public void dispose() {
        super.dispose();
        nativeDisposeFrame(nativePtr);
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

    private native long nativeCreateFrame();
    private native void nativeShowFrame(long ptr, int width, int height);
    private native void nativeHideFrame(long ptr);
    private native void nativeDisposeFrame(long ptr);

    private native long getWLSurface();

    // called from native code
    private void postWindowClosing() {
        WLToolkit.postEvent(new WindowEvent((Window) target, WindowEvent.WINDOW_CLOSING));
    }
}
