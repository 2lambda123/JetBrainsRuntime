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

import java.awt.GraphicsDevice;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import sun.java2d.SunGraphicsEnvironment;
import sun.java2d.SurfaceManagerFactory;
import sun.java2d.UnixSurfaceManagerFactory;
import sun.util.logging.PlatformLogger;
import sun.util.logging.PlatformLogger.Level;

public class WLGraphicsEnvironment extends SunGraphicsEnvironment {
    private static final PlatformLogger log = PlatformLogger.getLogger("sun.awt.wl.WLGraphicsEnvironment");

    private static boolean vulkanEnabled = false;
    @SuppressWarnings("removal")
    private static boolean vulkanRequested =
            AccessController.doPrivileged(
                    (PrivilegedAction<Boolean>) () ->
                            "true".equals(System.getProperty("sun.java2d.vulkan")));
    static {
        System.loadLibrary("awt");
        SurfaceManagerFactory.setInstance(new UnixSurfaceManagerFactory());
        if (vulkanRequested) {
            vulkanEnabled = initVKWL();
        }
        if (log.isLoggable(Level.INFO)) {
            log.info("Vulkan rendering enabled: " + (vulkanEnabled?"YES":"NO"));
        }
    }

    private static native boolean initVKWL();

    private WLGraphicsEnvironment() {
    }

    public static boolean isVulkanEnabled() {
        return vulkanEnabled;
    }

    private static class Holder {
        static final WLGraphicsEnvironment INSTANCE = new WLGraphicsEnvironment();
    }

    public static WLGraphicsEnvironment getSingleInstance() {
        return Holder.INSTANCE;
    }


    @Override
    protected int getNumScreens() {
        synchronized (devices) {
            return devices.size();
        }
    }

    @Override
    protected GraphicsDevice makeScreenDevice(int screenNum) {
        synchronized (devices) {
            return devices.get(screenNum);
        }
    }

    @Override
    public boolean isDisplayLocal() {
        return true;
    }

    private final List<WLGraphicsDevice> devices = new ArrayList<>(5);

    private void notifyOutputConfigured(String name, int wlID, int x, int y, int width, int height,
                                        int subpixel, int transform, int scale) {
        // Called from native code whenever a new output appears or an existing one changes its properties
        // NB: initially called during WLToolkit.initIDs() on the main thread; later on EDT.
        synchronized (devices) {
            boolean newOutput = true;
            for (final WLGraphicsDevice gd : devices) {
                if (gd.getWLID() == wlID) {
                    gd.updateConfiguration(name, x, y, width, height, scale);
                    newOutput = false;
                }
            }
            if (newOutput) {
                final WLGraphicsDevice gd = new WLGraphicsDevice(wlID);
                gd.updateConfiguration(name, x, y, width, height, scale);
                devices.add(gd);
            }
        }
        displayChanged();
    }

    private void notifyOutputDestroyed(int wlID) {
        // Called from native code whenever one of the outputs is no longer available.
        // All surfaces that were partly visible on that output should have
        // notifySurfaceLeftOutput().

        // NB: id may *not* be that of any output; if so, just ignore this event.
        synchronized (devices) {
            devices.removeIf(gd -> gd.getWLID() == wlID);
        }
    }

    WLGraphicsDevice notifySurfaceEnteredOutput(WLComponentPeer wlComponentPeer, int wlOutputID) {
        synchronized (devices) {
            for (WLGraphicsDevice gd : devices) {
                if (gd.getWLID() == wlOutputID) {
                    gd.addWindow(wlComponentPeer);
                    return gd;
                }
            }
            return null;
        }
    }

    WLGraphicsDevice notifySurfaceLeftOutput(WLComponentPeer wlComponentPeer, int wlOutputID) {
        synchronized (devices) {
            for (WLGraphicsDevice gd : devices) {
                if (gd.getWLID() == wlOutputID) {
                    gd.removeWindow(wlComponentPeer);
                    return gd;
                }
            }
            return null;
        }
    }
}
