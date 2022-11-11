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

package sun.awt.wl;

import sun.awt.AWTAccessor;
import sun.awt.DisplayChangedListener;
import sun.java2d.vulkan.WLVKGraphicsConfig;

import java.awt.*;
import java.util.ArrayList;

public class WLGraphicsDevice extends GraphicsDevice implements DisplayChangedListener {
    private final int wlID; // ID of wl_output object received from Wayland
    private String name;
    private int x;
    private int y;

    private final java.util.List<WLComponentPeer> peers = new ArrayList<>();
    private final WLGraphicsConfig config;

    public WLGraphicsDevice(int id) {
        this.wlID = id;
        config = WLGraphicsEnvironment.isVulkanEnabled() ?
                WLVKGraphicsConfig.getConfig(this) : new WLGraphicsConfig(this);
    }

    int getWLID() {
        return wlID;
    }

    void updateConfiguration(String name, int x, int y, int width, int height, int scale) {
        this.name = name == null ? "wl_output." + wlID : name;
        this.x = x;
        this.y = y;
        config.update(width, height, scale);
    }

    void updateConfiguration(WLGraphicsDevice gd) {
        final Rectangle bounds = config.getBounds();
        updateConfiguration(gd.name, gd.x, gd.y, bounds.width, bounds.height, config.getScale());
    }

    @Override
    public int getType() {
        return TYPE_RASTER_SCREEN;
    }

    @Override
    public String getIDstring() {
        return name;
    }

    @Override
    public GraphicsConfiguration[] getConfigurations() {
        // From wayland.xml, wl_output.mode event:
        // "Non-current modes are deprecated. A compositor can decide to only
        //	advertise the current mode and never send other modes. Clients
        //	should not rely on non-current modes."
        // So there is just one config, always.
        return new GraphicsConfiguration[] {config};
    }

    @Override
    public GraphicsConfiguration getDefaultConfiguration() {
        return config;
    }

    int getScale() {
        return config.getScale();
    }

    @Override
    public boolean isFullScreenSupported() {
        return true;
    }

    public void setFullScreenWindow(Window w) {
        Window old = getFullScreenWindow();
        if (w == old) {
            return;
        }

        super.setFullScreenWindow(w);

        if (isFullScreenSupported()) {
            if (w != null) {
                enterFullScreenExclusive(w);
            } else {
                exitFullScreenExclusive(old);
            }
        }
    }

    @Override
    public void displayChanged() {
       synchronized (peers) {
           peers.forEach(WLComponentPeer::displayChanged);
       }
    }

    @Override
    public void paletteChanged() {
    }

    private void enterFullScreenExclusive(Window w) {
        WLComponentPeer peer = AWTAccessor.getComponentAccessor().getPeer(w);
        if (peer != null) {
            peer.requestFullScreen(wlID);
        }
    }

    private void exitFullScreenExclusive(Window w) {
        WLComponentPeer peer = AWTAccessor.getComponentAccessor().getPeer(w);
        if (peer != null) {
            peer.requestUnsetFullScreen();
        }
    }

    public void addWindow(WLComponentPeer peer) {
        synchronized (peers) {
            peers.add(peer);
        }
    }

    public void removeWindow(WLComponentPeer peer) {
        synchronized (peers) {
            peers.remove(peer);
        }
    }
}
