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

import sun.awt.SunHints;
import sun.awt.image.PixelConverter;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.SurfaceDataProxy;
import sun.java2d.loops.CompositeType;
import sun.java2d.loops.GraphicsPrimitive;
import sun.java2d.loops.MaskFill;
import sun.java2d.loops.SurfaceType;
import sun.java2d.pipe.ParallelogramPipe;
import sun.java2d.pipe.PixelToParallelogramConverter;
import sun.java2d.pipe.RenderBuffer;
import sun.java2d.pipe.TextPipe;
import sun.java2d.pipe.hw.AccelSurface;

import java.awt.*;
import java.awt.image.ColorModel;
import java.awt.image.Raster;

import static sun.java2d.metal.MTLContext.MTLContextCaps.CAPS_EXT_LCD_SHADER;
import static sun.java2d.metal.MTLContext.MTLContextCaps.CAPS_EXT_TEXRECT;
import static sun.java2d.pipe.BufferedOpCodes.*;
import static sun.java2d.pipe.hw.ContextCapabilities.*;

public abstract class MTLSurfaceData extends SurfaceData
        implements AccelSurface {

    /**
     * Pixel formats
     */
    public static final int PF_INT_ARGB        = 0;
    public static final int PF_INT_ARGB_PRE    = 1;
    public static final int PF_INT_RGB         = 2;
    public static final int PF_INT_RGBX        = 3;
    public static final int PF_INT_BGR         = 4;
    public static final int PF_INT_BGRX        = 5;
    public static final int PF_USHORT_565_RGB  = 6;
    public static final int PF_USHORT_555_RGB  = 7;
    public static final int PF_USHORT_555_RGBX = 8;
    public static final int PF_BYTE_GRAY       = 9;
    public static final int PF_USHORT_GRAY     = 10;
    public static final int PF_3BYTE_BGR       = 11;
    /**
     * SurfaceTypes
     */

    private static final String DESC_MTL_SURFACE = "MTL Surface";
    private static final String DESC_MTL_SURFACE_RTT =
            "MTL Surface (render-to-texture)";
    private static final String DESC_MTL_TEXTURE = "MTL Texture";


    static final SurfaceType MTLSurface =
            SurfaceType.Any.deriveSubType(DESC_MTL_SURFACE,
                    PixelConverter.ArgbPre.instance);
    static final SurfaceType MTLSurfaceRTT =
            MTLSurface.deriveSubType(DESC_MTL_SURFACE_RTT);
    static final SurfaceType MTLTexture =
            SurfaceType.Any.deriveSubType(DESC_MTL_TEXTURE);

    protected static MTLRenderer mtlRenderPipe;
    protected static PixelToParallelogramConverter mtlTxRenderPipe;
    protected static ParallelogramPipe mtlAAPgramPipe;
    protected static MTLTextRenderer mtlTextPipe;
    protected static MTLDrawImage mtlImagePipe;
    /** This will be true if the fbobject system property has been enabled. */
    private static boolean isFBObjectEnabled;
    /** This will be true if the lcdshader system property has been enabled.*/
    private static boolean isLCDShaderEnabled;
    /** This will be true if the biopshader system property has been enabled.*/
    private static boolean isBIOpShaderEnabled;
    /** This will be true if the gradshader system property has been enabled.*/
    private static boolean isGradShaderEnabled;

    static {
        if (!GraphicsEnvironment.isHeadless()) {
            // fbobject currently enabled by default; use "false" to disable
            String fbo = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction(
                            "java2d.metal.fbobject"));
            isFBObjectEnabled = !"false".equals(fbo);

            // lcdshader currently enabled by default; use "false" to disable
            String lcd = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction(
                            "java2d.metal.lcdshader"));
            isLCDShaderEnabled = !"false".equals(lcd);

            // biopshader currently enabled by default; use "false" to disable
            String biop = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction(
                            "java2d.metal.biopshader"));
            isBIOpShaderEnabled = !"false".equals(biop);

            // gradshader currently enabled by default; use "false" to disable
            String grad = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction(
                            "java2d.metal.gradshader"));
            isGradShaderEnabled = !"false".equals(grad);

            MTLRenderQueue rq = MTLRenderQueue.getInstance();
            mtlImagePipe = new MTLDrawImage();
            mtlTextPipe = new MTLTextRenderer(rq);
            mtlRenderPipe = new MTLRenderer(rq);
            if (GraphicsPrimitive.tracingEnabled()) {
                mtlTextPipe = mtlTextPipe.traceWrap();
                //The wrapped mtlRenderPipe will wrap the AA pipe as well...
                //mtlAAPgramPipe = mtlRenderPipe.traceWrap();
            }
            mtlAAPgramPipe = mtlRenderPipe.getAAParallelogramPipe();
            mtlTxRenderPipe =
                    new PixelToParallelogramConverter(mtlRenderPipe,
                            mtlRenderPipe,
                            1.0, 0.25, true);

            MTLBlitLoops.register();
            MTLMaskFill.register();
            MTLMaskBlit.register();
        }
    }

    protected final int scale;
    protected final int width;
    protected final int height;
    protected int type;
    private MTLGraphicsConfig graphicsConfig;
    // these fields are set from the native code when the surface is
    // initialized
    private int nativeWidth;
    private int nativeHeight;

    /**
     * Returns the appropriate SurfaceType corresponding to the given OpenGL
     * surface type constant (e.g. TEXTURE -> MTLTexture).
     */
    private static SurfaceType getCustomSurfaceType(int oglType) {
        switch (oglType) {
            case TEXTURE:
                return MTLTexture;
            case RT_TEXTURE:
                return MTLSurfaceRTT;
            default:
                return MTLSurface;
        }
    }

    static void swapBuffers(long window) {
        MTLRenderQueue rq = MTLRenderQueue.getInstance();
        rq.lock();
        try {
            RenderBuffer buf = rq.getBuffer();
            rq.ensureCapacityAndAlignment(12, 4);
            buf.putInt(SWAP_BUFFERS);
            buf.putLong(window);
            rq.flushNow();
        } finally {
            rq.unlock();
        }
    }

    private native void initOps(long pConfigInfo, long pPeerData, long layerPtr,
                                int xoff, int yoff, boolean isOpaque);

    private MTLSurfaceData(MTLLayer layer, MTLGraphicsConfig gc,
                           ColorModel cm, int type, int width, int height)
    {
        super(getCustomSurfaceType(type), cm);
        this.graphicsConfig = gc;
        this.type = type;
        setBlitProxyKey(gc.getProxyKey());

        // TEXTURE shouldn't be scaled, it is used for managed BufferedImages.
        scale = type == TEXTURE ? 1 : gc.getDevice().getScaleFactor();
        this.width = width * scale;
        this.height = height * scale;

        long pConfigInfo = gc.getNativeConfigInfo();
        long layerPtr = 0L;
        boolean isOpaque = true;
        if (layer != null) {
            layerPtr = layer.getPointer();
            isOpaque = layer.isOpaque();
        }
        MTLGraphicsConfig.refPConfigInfo(pConfigInfo);
        initOps(pConfigInfo, 0, layerPtr, 0, 0, isOpaque);
    }

    @Override //SurfaceData
    public GraphicsConfiguration getDeviceConfiguration() {
        return graphicsConfig;
    }

    /**
     * Creates a SurfaceData object representing the intermediate buffer
     * between the Java2D flusher thread and the AppKit thread.
     */
    public static MTLLayerSurfaceData createData(MTLLayer layer) {
        MTLGraphicsConfig gc = (MTLGraphicsConfig)layer.getGraphicsConfiguration();
        Rectangle r = layer.getBounds();
        return new MTLLayerSurfaceData(layer, gc, r.width, r.height);
    }

    /**
     * Creates a SurfaceData object representing an off-screen buffer (either a
     * FBO or Texture).
     */
    public static MTLOffScreenSurfaceData createData(MTLGraphicsConfig gc,
                                                     int width, int height, ColorModel cm, Image image, int type) {
        return new MTLOffScreenSurfaceData(gc, width, height, image, cm,
                type);
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
    public Rectangle getBounds() {
        return new Rectangle(width, height);
    }

    protected native void clearWindow();

    protected native boolean initTexture(long pData, boolean isOpaque, int width, int height);

    protected native boolean initRTexture(long pData, boolean isOpaque, int width, int height);

    protected native boolean initFlipBackbuffer(long pData);

    @Override
    public SurfaceDataProxy makeProxyFor(SurfaceData srcData) {
        return MTLSurfaceDataProxy.createProxy(srcData, graphicsConfig);
    }

    /**
     * Note: This should only be called from the QFT under the AWT lock.
     * This method is kept separate from the initSurface() method below just
     * to keep the code a bit cleaner.
     */
    private void initSurfaceNow(int width, int height) {
        boolean isOpaque = (getTransparency() == Transparency.OPAQUE);
        boolean success = false;

        switch (type) {
            case TEXTURE:
                success = initTexture(getNativeOps(), isOpaque, width, height);
                break;

            case RT_TEXTURE:
                success = initRTexture(getNativeOps(), isOpaque, width, height);
                break;

            case FLIP_BACKBUFFER:
                success = initFlipBackbuffer(getNativeOps());
                break;

            default:
                break;
        }

        if (!success) {
            throw new OutOfMemoryError("can't create offscreen surface");
        }
    }

    /**
     * Initializes the appropriate OpenGL offscreen surface based on the value
     * of the type parameter.  If the surface creation fails for any reason,
     * an OutOfMemoryError will be thrown.
     */
    protected void initSurface(final int width, final int height) {
        MTLRenderQueue rq = MTLRenderQueue.getInstance();
        rq.lock();
        try {
            switch (type) {
                case TEXTURE:
                case RT_TEXTURE:
                    // need to make sure the context is current before
                    // creating the texture or fbobject
                    MTLContext.setScratchSurface(graphicsConfig);
                    break;
                default:
                    break;
            }
            rq.flushAndInvokeNow(new Runnable() {
                public void run() {
                    initSurfaceNow(width, height);
                }
            });
        } finally {
            rq.unlock();
        }
    }

    /**
     * Returns the MTLContext for the GraphicsConfig associated with this
     * surface.
     */
    public final MTLContext getContext() {
        return graphicsConfig.getContext();
    }

    /**
     * Returns the MTLGraphicsConfig associated with this surface.
     */
    final MTLGraphicsConfig getMTLGraphicsConfig() {
        return graphicsConfig;
    }

    /**
     * Returns one of the surface type constants defined above.
     */
    public final int getType() {
        return type;
    }

    /**
     * For now, we can only render LCD text if:
     *   - the fragment shader extension is available, and
     *   - the source color is opaque, and
     *   - blending is SrcOverNoEa or disabled
     *   - and the destination is opaque
     *
     * Eventually, we could enhance the native OGL text rendering code
     * and remove the above restrictions, but that would require significantly
     * more code just to support a few uncommon cases.
     */
    public boolean canRenderLCDText(SunGraphics2D sg2d) {
        return
                graphicsConfig.isCapPresent(CAPS_EXT_LCD_SHADER) &&
                        sg2d.surfaceData.getTransparency() == Transparency.OPAQUE &&
                        sg2d.paintState <= SunGraphics2D.PAINT_OPAQUECOLOR &&
                        (sg2d.compositeState <= SunGraphics2D.COMP_ISCOPY ||
                                (sg2d.compositeState <= SunGraphics2D.COMP_ALPHA && canHandleComposite(sg2d.composite)));
    }

    private boolean canHandleComposite(Composite c) {
        if (c instanceof AlphaComposite) {
            AlphaComposite ac = (AlphaComposite)c;

            return ac.getRule() == AlphaComposite.SRC_OVER && ac.getAlpha() >= 1f;
        }
        return false;
    }

    public void validatePipe(SunGraphics2D sg2d) {
        TextPipe textpipe;
        boolean validated = false;

        // MTLTextRenderer handles both AA and non-AA text, but
        // only works with the following modes:
        // (Note: For LCD text we only enter this code path if
        // canRenderLCDText() has already validated that the mode is
        // CompositeType.SrcNoEa (opaque color), which will be subsumed
        // by the CompositeType.SrcNoEa (any color) test below.)

        if (/* CompositeType.SrcNoEa (any color) */
                (sg2d.compositeState <= SunGraphics2D.COMP_ISCOPY &&
                        sg2d.paintState <= SunGraphics2D.PAINT_ALPHACOLOR)         ||

                        /* CompositeType.SrcOver (any color) */
                        (sg2d.compositeState == SunGraphics2D.COMP_ALPHA   &&
                                sg2d.paintState <= SunGraphics2D.PAINT_ALPHACOLOR &&
                                (((AlphaComposite)sg2d.composite).getRule() ==
                                        AlphaComposite.SRC_OVER))                                 ||

                        /* CompositeType.Xor (any color) */
                        (sg2d.compositeState == SunGraphics2D.COMP_XOR &&
                                sg2d.paintState <= SunGraphics2D.PAINT_ALPHACOLOR))
        {
            textpipe = mtlTextPipe;
        } else {
            // do this to initialize textpipe correctly; we will attempt
            // to override the non-text pipes below
            super.validatePipe(sg2d);
            textpipe = sg2d.textpipe;
            validated = true;
        }

        PixelToParallelogramConverter txPipe = null;
        MTLRenderer nonTxPipe = null;

        if (sg2d.antialiasHint != SunHints.INTVAL_ANTIALIAS_ON) {
            if (sg2d.paintState <= SunGraphics2D.PAINT_ALPHACOLOR) {
                if (sg2d.compositeState <= SunGraphics2D.COMP_XOR) {
                    txPipe = mtlTxRenderPipe;
                    nonTxPipe = mtlRenderPipe;
                }
            } else if (sg2d.compositeState <= SunGraphics2D.COMP_ALPHA) {
                if (MTLPaints.isValid(sg2d)) {
                    txPipe = mtlTxRenderPipe;
                    nonTxPipe = mtlRenderPipe;
                }
                // custom paints handled by super.validatePipe() below
            }
        } else {
            if (sg2d.paintState <= SunGraphics2D.PAINT_ALPHACOLOR) {
                if (graphicsConfig.isCapPresent(CAPS_PS30) &&
                        (sg2d.imageComp == CompositeType.SrcOverNoEa ||
                                sg2d.imageComp == CompositeType.SrcOver))
                {
                    if (!validated) {
                        super.validatePipe(sg2d);
                        validated = true;
                    }
                    PixelToParallelogramConverter aaConverter =
                            new PixelToParallelogramConverter(sg2d.shapepipe,
                                    mtlAAPgramPipe,
                                    1.0/8.0, 0.499,
                                    false);
                    sg2d.drawpipe = aaConverter;
                    sg2d.fillpipe = aaConverter;
                    sg2d.shapepipe = aaConverter;
                } else if (sg2d.compositeState == SunGraphics2D.COMP_XOR) {
                    // install the solid pipes when AA and XOR are both enabled
                    txPipe = mtlTxRenderPipe;
                    nonTxPipe = mtlRenderPipe;
                }
            }
            // other cases handled by super.validatePipe() below
        }

        if (txPipe != null) {
            if (sg2d.transformState >= SunGraphics2D.TRANSFORM_TRANSLATESCALE) {
                sg2d.drawpipe = txPipe;
                sg2d.fillpipe = txPipe;
            } else if (sg2d.strokeState != SunGraphics2D.STROKE_THIN) {
                sg2d.drawpipe = txPipe;
                sg2d.fillpipe = nonTxPipe;
            } else {
                sg2d.drawpipe = nonTxPipe;
                sg2d.fillpipe = nonTxPipe;
            }
            // Note that we use the transforming pipe here because it
            // will examine the shape and possibly perform an optimized
            // operation if it can be simplified.  The simplifications
            // will be valid for all STROKE and TRANSFORM types.
            sg2d.shapepipe = txPipe;
        } else {
            if (!validated) {
                super.validatePipe(sg2d);
            }
        }

        // install the text pipe based on our earlier decision
        sg2d.textpipe = textpipe;

        // always override the image pipe with the specialized OGL pipe
        sg2d.imagepipe = mtlImagePipe;
    }

    @Override
    protected MaskFill getMaskFill(SunGraphics2D sg2d) {
        if (sg2d.paintState > SunGraphics2D.PAINT_ALPHACOLOR) {
            /*
             * We can only accelerate non-Color MaskFill operations if
             * all of the following conditions hold true:
             *   - there is an implementation for the given paintState
             *   - the current Paint can be accelerated for this destination
             *   - multitexturing is available (since we need to modulate
             *     the alpha mask texture with the paint texture)
             *
             * In all other cases, we return null, in which case the
             * validation code will choose a more general software-based loop.
             */
            if (!MTLPaints.isValid(sg2d) ||
                    !graphicsConfig.isCapPresent(CAPS_MULTITEXTURE))
            {
                return null;
            }
        }
        return super.getMaskFill(sg2d);
    }

    public void flush() {
        invalidate();
        MTLRenderQueue rq = MTLRenderQueue.getInstance();
        rq.lock();
        try {
            // make sure we have a current context before
            // disposing the native resources (e.g. texture object)
            MTLContext.setScratchSurface(graphicsConfig);

            RenderBuffer buf = rq.getBuffer();
            rq.ensureCapacityAndAlignment(12, 4);
            buf.putInt(FLUSH_SURFACE);
            buf.putLong(getNativeOps());

            // this call is expected to complete synchronously, so flush now
            rq.flushNow();
        } finally {
            rq.unlock();
        }
    }

    /**
     * Returns true if the surface is an on-screen window surface or
     * a FBO texture attached to an on-screen CALayer.
     *
     * Needed by Mac OS X port.
     */
    public boolean isOnScreen() {
        return getType() == WINDOW;
    }

    private native long getMTLTexturePointer(long pData);

    /**
     * Returns native resource of specified {@code resType} associated with
     * this surface.
     *
     * Specifically, for {@code MTLSurfaceData} this method returns the
     * the following:
     * <pre>
     * TEXTURE              - texture id
     * </pre>
     *
     * Note: the resource returned by this method is only valid on the rendering
     * thread.
     *
     * @return native resource of specified type or 0L if
     * such resource doesn't exist or can not be retrieved.
     * @see AccelSurface#getNativeResource
     */
    public long getNativeResource(int resType) {
        if (resType == TEXTURE) {
            return getMTLTexturePointer(getNativeOps());
        }
        return 0L;
    }

    public Raster getRaster(int x, int y, int w, int h) {
        throw new InternalError("not implemented yet");
    }

    @Override
    public boolean copyArea(SunGraphics2D sg2d, int x, int y, int w, int h,
                            int dx, int dy) {
        if (sg2d.compositeState >= SunGraphics2D.COMP_XOR) {
            return false;
        }
        mtlRenderPipe.copyArea(sg2d, x, y, w, h, dx, dy);
        return true;
    }

    public Rectangle getNativeBounds() {
        MTLRenderQueue rq = MTLRenderQueue.getInstance();
        rq.lock();
        try {
            return new Rectangle(nativeWidth, nativeHeight);
        } finally {
            rq.unlock();
        }
    }

    /**
     * A surface which implements an intermediate buffer between
     * the Java2D flusher thread and the AppKit thread.
     *
     * This surface serves as a buffer attached to a MTLLayer and
     * the layer redirects all painting to the buffer's graphics.
     */
    public static class MTLLayerSurfaceData extends MTLSurfaceData {

        private final MTLLayer layer;

        private MTLLayerSurfaceData(MTLLayer layer, MTLGraphicsConfig gc,
                                   int width, int height) {
            super(layer, gc, gc.getColorModel(), RT_TEXTURE, width, height);
            this.layer = layer;
            initSurface(this.width, this.height);
        }

        @Override
        public SurfaceData getReplacement() {
            return layer.getSurfaceData();
        }

        @Override
        public boolean isOnScreen() {
            return true;
        }

        @Override
        public Object getDestination() {
            return layer.getDestination();
        }

        @Override
        public int getTransparency() {
            return layer.getTransparency();
        }

        @Override
        public void invalidate() {
            super.invalidate();
            clearWindow();
        }
    }

    /**
     * SurfaceData object representing an off-screen buffer (either a FBO or
     * Texture).
+     */
    public static class MTLOffScreenSurfaceData extends MTLSurfaceData {
        private final Image offscreenImage;

        public MTLOffScreenSurfaceData(MTLGraphicsConfig gc, int width,
                                       int height, Image image,
                                       ColorModel cm, int type) {
            super(null, gc, cm, type, width, height);
            offscreenImage = image;
            initSurface(this.width, this.height);
        }

        @Override
        public SurfaceData getReplacement() {
            return restoreContents(offscreenImage);
        }

        /**
         * Returns destination Image associated with this SurfaceData.
         */
        @Override
        public Object getDestination() {
            return offscreenImage;
        }
    }


    // additional cleanup
    private static native void destroyCGLContext(long ctx);

    public static void destroyOGLContext(long ctx) {
        if (ctx != 0L) {
            destroyCGLContext(ctx);
        }
    }

    /**
     * Disposes the native resources associated with the given MTLSurfaceData
     * (referenced by the pData parameter).  This method is invoked from
     * the native Dispose() method from the Disposer thread when the
     * Java-level MTLSurfaceData object is about to go away.
     */
     public static void dispose(long pData, long pConfigInfo) {
        MTLRenderQueue rq = MTLRenderQueue.getInstance();
        rq.lock();
        try {
            RenderBuffer buf = rq.getBuffer();
            rq.ensureCapacityAndAlignment(12, 4);
            buf.putInt(DISPOSE_SURFACE);
            buf.putLong(pData);

            // this call is expected to complete synchronously, so flush now
            rq.flushNow();
        } finally {
            rq.unlock();
        }

        MTLGraphicsConfig.deRefPConfigInfo(pConfigInfo);
    }
}
