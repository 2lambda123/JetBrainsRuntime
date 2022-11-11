/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, JetBrains s.r.o.. All rights reserved.
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

package sun.java2d.vulkan;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.ColorModel;
import sun.awt.wl.WLComponentPeer;
import sun.java2d.SurfaceData;
import sun.java2d.pipe.BufferedContext;
import sun.java2d.wl.WLSurfaceData;

public abstract class WLVKSurfaceData extends VKSurfaceData {

    protected WLComponentPeer peer;
    protected WLVKGraphicsConfig graphicsConfig;

    protected WLVKSurfaceData(WLComponentPeer peer, WLVKGraphicsConfig gc,
                              ColorModel cm, int type)
    {
        super(gc, cm, type, 0, 0);
        this.peer = peer;
        this.graphicsConfig = gc;
    }

    @Override
    public boolean isOnScreen() {
        return false;
    }

    public GraphicsConfiguration getDeviceConfiguration() {
        return graphicsConfig;
    }

    /**
     * Creates a SurfaceData object representing the back buffer of a
     * double-buffered on-screen Window.
     */
    public static WLVKOffScreenSurfaceData createData(WLComponentPeer peer,
                                                     Image image,
                                                     int type)
    {
        WLVKGraphicsConfig gc = getGC(peer);
        Rectangle r = peer.getVisibleBounds();
            return new WLVKOffScreenSurfaceData(peer, gc, r.width, r.height,
                                               image, peer.getColorModel(),
                                               type);
    }

    /**
     * Creates a SurfaceData object representing an off-screen buffer (either
     * a FBO or Texture).
     */
    public static WLVKOffScreenSurfaceData createData(WLVKGraphicsConfig gc,
                                                     int width, int height,
                                                     ColorModel cm,
                                                     Image image, int type)
    {
        return new WLVKOffScreenSurfaceData(null, gc, width, height,
                                           image, cm, type);
    }

    public static WLVKGraphicsConfig getGC(WLComponentPeer peer) {
        if (peer != null) {
            return (WLVKGraphicsConfig) peer.getGraphicsConfiguration();
        } else {
            // REMIND: this should rarely (never?) happen, but what if
            //         default config is not WLVK?
            GraphicsEnvironment env =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = env.getDefaultScreenDevice();
            return (WLVKGraphicsConfig)gd.getDefaultConfiguration();
        }
    }

    public static class WLVKWindowSurfaceData extends WLVKSurfaceData {
        protected final int scale;

        public WLVKWindowSurfaceData(WLComponentPeer peer, WLVKGraphicsConfig gc)
        {
            super(peer, gc, peer.getColorModel(), WINDOW);
            scale = 1;
        }

        public SurfaceData getReplacement() {
            return WLSurfaceData.createData(peer);
        }

        @Override
        public long getNativeResource(int resType) {
            return 0;
        }

        public Rectangle getBounds() {
            Rectangle r = peer.getVisibleBounds();
            r.x = r.y = 0;
            r.width = (int) Math.ceil(r.width * scale);
            r.height = (int) Math.ceil(r.height * scale);
            return r;
        }

        /**
         * Returns destination Component associated with this SurfaceData.
         */
        public Object getDestination() {
            return peer.getTarget();
        }

        @Override
        public double getDefaultScaleX() {
            return scale;
        }

        @Override
        public double getDefaultScaleY() {
            return scale;
        }

        @Override
        public BufferedContext getContext() {
            return graphicsConfig.getContext();
        }

        @Override
        public boolean isOnScreen() {
            return true;
        }
    }

    public static class WLVKOffScreenSurfaceData extends WLVKSurfaceData {

        private Image offscreenImage;
        private int width, height;
        private final int scale;

        public WLVKOffScreenSurfaceData(WLComponentPeer peer,
                                       WLVKGraphicsConfig gc,
                                       int width, int height,
                                       Image image, ColorModel cm,
                                       int type)
        {
            super(peer, gc, cm, type);

            scale = 1;
            this.width = width * scale;
            this.height = height * scale;
            offscreenImage = image;
        }

        public SurfaceData getReplacement() {
            return restoreContents(offscreenImage);
        }

        @Override
        public long getNativeResource(int resType) {
            return 0;
        }

        public Rectangle getBounds() {
            if (type == FLIP_BACKBUFFER) {
                Rectangle r = peer.getVisibleBounds();
                r.x = r.y = 0;
                r.width = (int) Math.ceil(r.width * scale);
                r.height = (int) Math.ceil(r.height * scale);
                return r;
            } else {
                return new Rectangle(width, height);
            }
        }

        /**
         * Returns destination Image associated with this SurfaceData.
         */
        public Object getDestination() {
            return offscreenImage;
        }

        @Override
        public double getDefaultScaleX() {
            return scale;
        }

        @Override
        public double getDefaultScaleY() {
            return scale;
        }

        @Override
        public BufferedContext getContext() {
            return graphicsConfig.getContext();
        }
    }
}
