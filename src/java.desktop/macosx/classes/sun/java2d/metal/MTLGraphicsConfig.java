/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.metal;

import sun.awt.CGraphicsConfig;
import sun.awt.CGraphicsDevice;
import sun.awt.image.OffScreenImage;
import sun.awt.image.SunVolatileImage;
import sun.awt.image.SurfaceManager;
import sun.java2d.Disposer;
import sun.java2d.DisposerRecord;
import sun.java2d.Surface;
import sun.java2d.SurfaceData;
import sun.java2d.pipe.hw.AccelGraphicsConfig;
import sun.java2d.pipe.hw.AccelSurface;
import sun.java2d.pipe.hw.AccelTypedVolatileImage;
import sun.java2d.pipe.hw.ContextCapabilities;
import sun.lwawt.LWComponentPeer;
import sun.lwawt.macosx.CFRetainedResource;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;

import static sun.java2d.opengl.OGLSurfaceData.TEXTURE;
import static sun.java2d.pipe.hw.AccelSurface.RT_TEXTURE;
import static sun.java2d.pipe.hw.ContextCapabilities.*;

public final class MTLGraphicsConfig extends CGraphicsConfig
        implements AccelGraphicsConfig, SurfaceManager.ProxiedGraphicsConfig
{
    //private static final int kOpenGLSwapInterval =
    // RuntimeOptions.getCurrentOptions().OpenGLSwapInterval;
    private static final int kOpenGLSwapInterval = 0; // TODO
    private static boolean mtlAvailable;
    private static ImageCapabilities imageCaps = new MTLImageCaps();

    private static final String mtlShadersLib = AccessController.doPrivileged(
            (PrivilegedAction<String>) () ->
                    System.getProperty("java.home", "") + File.separator +
                            "lib" + File.separator + "shaders.metallib");


    private int pixfmt;
    private BufferCapabilities bufferCaps;
    private long pConfigInfo;
    private ContextCapabilities mtlCaps;
    private final MTLContext context;
    private final Object disposerReferent = new Object();
    private final int maxTextureSize;

    private static native boolean initMTL();
    private static native long getMTLConfigInfo(int displayID, String mtlShadersLib);

    /**
     * Returns GL_MAX_TEXTURE_SIZE from the shared opengl context. Must be
     * called under OGLRQ lock, because this method change current context.
     *
     * @return GL_MAX_TEXTURE_SIZE
     */
    private static native int nativeGetMaxTextureSize();

    private static final HashMap<Long, Integer> pGCRefCounts = new HashMap<>();

    static {
        mtlAvailable = initMTL();
    }

    private MTLGraphicsConfig(CGraphicsDevice device, int pixfmt,
                              long configInfo, int maxTextureSize,
                              ContextCapabilities mtlCaps) {
        super(device);

        this.pixfmt = pixfmt;
        this.pConfigInfo = configInfo;
        this.mtlCaps = mtlCaps;
        this.maxTextureSize = maxTextureSize;
        context = new MTLContext(MTLRenderQueue.getInstance());
        refPConfigInfo(pConfigInfo);
        // add a record to the Disposer so that we destroy the native
        // MTLGraphicsConfigInfo data when this object goes away
        Disposer.addRecord(disposerReferent,
                new MTLGCDisposerRecord(pConfigInfo));
    }

    @Override
    public Object getProxyKey() {
        return this;
    }

    public SurfaceData createManagedSurface(int w, int h, int transparency) {
        return MTLSurfaceData.createData(this, w, h,
                getColorModel(transparency),
                null,
                MTLSurfaceData.TEXTURE);
    }

    public static MTLGraphicsConfig getConfig(CGraphicsDevice device,
                                              int displayID, int pixfmt)
    {
        if (!mtlAvailable) {
            return null;
        }

        long cfginfo = 0;
        int textureSize = 0;
        final String[] ids = new String[1];
        MTLRenderQueue rq = MTLRenderQueue.getInstance();
        rq.lock();
        try {
            // getCGLConfigInfo() creates and destroys temporary
            // surfaces/contexts, so we should first invalidate the current
            // Java-level context and flush the queue...
            MTLContext.invalidateCurrentContext();
            cfginfo = getMTLConfigInfo(displayID, mtlShadersLib);
            if (cfginfo != 0L) {
                textureSize = nativeGetMaxTextureSize();
                // 7160609: GL still fails to create a square texture of this
                // size. Half should be safe enough.
                // Explicitly not support a texture more than 2^14, see 8010999.
                textureSize = textureSize <= 16384 ? textureSize / 2 : 8192;
                MTLContext.setScratchSurface(cfginfo);
                rq.flushAndInvokeNow(() -> {
                    ids[0] = MTLContext.getMTLIdString();
                });
            }
        } finally {
            rq.unlock();
        }
        if (cfginfo == 0) {
            return null;
        }

        ContextCapabilities caps = new MTLContext.MTLContextCaps(
                CAPS_PS30 | CAPS_PS20 | CAPS_RT_PLAIN_ALPHA |
                        CAPS_RT_TEXTURE_ALPHA | CAPS_RT_TEXTURE_OPAQUE |
                        CAPS_MULTITEXTURE | CAPS_TEXNONPOW2 | CAPS_TEXNONSQUARE,
                ids[0]);
        return new MTLGraphicsConfig(device, pixfmt, cfginfo, textureSize, caps);
    }

    static void refPConfigInfo(long pConfigInfo) {
        synchronized (pGCRefCounts) {
            Integer count = pGCRefCounts.get(pConfigInfo);
            if (count == null) {
                count = 1;
            }
            else {
                count++;
            }
            pGCRefCounts.put(pConfigInfo, count);
        }
    }

    static void deRefPConfigInfo(long pConfigInfo) {
        synchronized (pGCRefCounts) {
            Integer count = pGCRefCounts.get(pConfigInfo);
            if (count != null) {
                count--;
                pGCRefCounts.put(pConfigInfo, count);
                if (count == 0) {
                    MTLRenderQueue.disposeGraphicsConfig(pConfigInfo);
                    pGCRefCounts.remove(pConfigInfo);
                }
            }
        }
    }

    /**
     * Returns true if the provided capability bit is present for this config.
     * See MTLContext.java for a list of supported capabilities.
     */
    public boolean isCapPresent(int cap) {
        return ((mtlCaps.getCaps() & cap) != 0);
    }

    public long getNativeConfigInfo() {
        return pConfigInfo;
    }

    /**
     * {@inheritDoc}
     *
     * @see sun.java2d.pipe.hw.BufferedContextProvider#getContext
     */
    @Override
    public MTLContext getContext() {
        return context;
    }

    @Override
    public BufferedImage createCompatibleImage(int width, int height) {
        ColorModel model = new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
        WritableRaster
                raster = model.createCompatibleWritableRaster(width, height);
        return new BufferedImage(model, raster, model.isAlphaPremultiplied(),
                null);
    }

    @Override
    public ColorModel getColorModel(int transparency) {
        switch (transparency) {
            case Transparency.OPAQUE:
                // REMIND: once the ColorModel spec is changed, this should be
                //         an opaque premultiplied DCM...
                return new DirectColorModel(24, 0xff0000, 0xff00, 0xff);
            case Transparency.BITMASK:
                return new DirectColorModel(25, 0xff0000, 0xff00, 0xff, 0x1000000);
            case Transparency.TRANSLUCENT:
                ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                return new DirectColorModel(cs, 32,
                        0xff0000, 0xff00, 0xff, 0xff000000,
                        true, DataBuffer.TYPE_INT);
            default:
                return null;
        }
    }

    public boolean isDoubleBuffered() {
        return true;
    }

    private static class MTLGCDisposerRecord implements DisposerRecord {
        private long pCfgInfo;
        public MTLGCDisposerRecord(long pCfgInfo) {
            this.pCfgInfo = pCfgInfo;
        }
        public void dispose() {
            if (pCfgInfo != 0) {
                deRefPConfigInfo(pCfgInfo);
                pCfgInfo = 0;
            }
        }
    }

    // TODO: CGraphicsConfig doesn't implement displayChanged() yet
    //@Override
    public synchronized void displayChanged() {
        //super.displayChanged();

        // the context could hold a reference to a MTLSurfaceData, which in
        // turn has a reference back to this MTLGraphicsConfig, so in order
        // for this instance to be disposed we need to break the connection
        MTLRenderQueue rq = MTLRenderQueue.getInstance();
        rq.lock();
        try {
            MTLContext.invalidateCurrentContext();
        } finally {
            rq.unlock();
        }
    }

    @Override
    public String toString() {
        return ("MTLGraphicsConfig[" + getDevice().getIDstring() +
                ",pixfmt="+pixfmt+"]");
    }

    @Override
    public SurfaceData createSurfaceData(CFRetainedResource layer) {
        return MTLSurfaceData.createData((MTLLayer) layer);
    }

    @Override
    public Image createAcceleratedImage(Component target,
                                        int width, int height)
    {
        ColorModel model = getColorModel(Transparency.OPAQUE);
        WritableRaster wr = model.createCompatibleWritableRaster(width, height);
        return new OffScreenImage(target, model, wr,
                model.isAlphaPremultiplied());
    }

    @Override
    public void assertOperationSupported(final int numBuffers,
                                         final BufferCapabilities caps)
            throws AWTException {
        // Assume this method is never called with numBuffers != 2, as 0 is
        // unsupported, and 1 corresponds to a SingleBufferStrategy which
        // doesn't depend on the peer. Screen is considered as a separate
        // "buffer".
        if (numBuffers != 2) {
            throw new AWTException("Only double buffering is supported");
        }
        final BufferCapabilities configCaps = getBufferCapabilities();
        if (!configCaps.isPageFlipping()) {
            throw new AWTException("Page flipping is not supported");
        }
        if (caps.getFlipContents() == BufferCapabilities.FlipContents.PRIOR) {
            throw new AWTException("FlipContents.PRIOR is not supported");
        }
    }

    @Override
    public Image createBackBuffer(final LWComponentPeer<?, ?> peer) {
        final Rectangle r = peer.getBounds();
        // It is possible for the component to have size 0x0, adjust it to
        // be at least 1x1 to avoid IAE
        final int w = Math.max(1, r.width);
        final int h = Math.max(1, r.height);
        final int transparency = peer.isTranslucent() ? Transparency.TRANSLUCENT
                : Transparency.OPAQUE;
        return new SunVolatileImage(this, w, h, transparency, null);
    }

    @Override
    public void destroyBackBuffer(final Image backBuffer) {
        if (backBuffer != null) {
            backBuffer.flush();
        }
    }

    @Override
    public void flip(final LWComponentPeer<?, ?> peer, final Image backBuffer,
                     final int x1, final int y1, final int x2, final int y2,
                     final BufferCapabilities.FlipContents flipAction) {
        final Graphics g = peer.getGraphics();
        try {
            g.drawImage(backBuffer, x1, y1, x2, y2, x1, y1, x2, y2, null);
        } finally {
            g.dispose();
        }
        if (flipAction == BufferCapabilities.FlipContents.BACKGROUND) {
            final Graphics2D bg = (Graphics2D) backBuffer.getGraphics();
            try {
                bg.setBackground(peer.getBackground());
                bg.clearRect(0, 0, backBuffer.getWidth(null),
                        backBuffer.getHeight(null));
            } finally {
                bg.dispose();
            }
        }
    }

    private static class MTLBufferCaps extends BufferCapabilities {
        public MTLBufferCaps(boolean dblBuf) {
            super(imageCaps, imageCaps,
                    dblBuf ? FlipContents.UNDEFINED : null);
        }
    }

    @Override
    public BufferCapabilities getBufferCapabilities() {
        if (bufferCaps == null) {
            bufferCaps = new MTLBufferCaps(isDoubleBuffered());
        }
        return bufferCaps;
    }

    private static class MTLImageCaps extends ImageCapabilities {
        private MTLImageCaps() {
            super(true);
        }
        public boolean isTrueVolatile() {
            return true;
        }
    }

    @Override
    public ImageCapabilities getImageCapabilities() {
        return imageCaps;
    }

    @Override
    public VolatileImage createCompatibleVolatileImage(int width, int height,
                                                       int transparency,
                                                       int type) {
        if (type != RT_TEXTURE && type != TEXTURE) {
            return null;
        }

        SunVolatileImage vi = new AccelTypedVolatileImage(this, width, height,
                transparency, type);
        Surface sd = vi.getDestSurface();
        if (!(sd instanceof AccelSurface) ||
                ((AccelSurface)sd).getType() != type)
        {
            vi.flush();
            vi = null;
        }

        return vi;
    }

    /**
     * {@inheritDoc}
     *
     * @see sun.java2d.pipe.hw.AccelGraphicsConfig#getContextCapabilities
     */
    @Override
    public ContextCapabilities getContextCapabilities() {
        return mtlCaps;
    }

    @Override
    public int getMaxTextureWidth() {
        return Math.max(maxTextureSize / getDevice().getScaleFactor(),
                getBounds().width);
    }

    @Override
    public int getMaxTextureHeight() {
        return Math.max(maxTextureSize / getDevice().getScaleFactor(),
                getBounds().height);
    }
}
