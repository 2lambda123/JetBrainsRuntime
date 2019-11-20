#ifndef MTLTexturePool_h_Included
#define MTLTexturePool_h_Included
#import <Metal/Metal.h>

@interface MTLTexturePoolItem : NSObject
@property (readwrite, retain) id<MTLTexture> texture;
@property (readwrite) bool isBusy;
@property (readwrite, retain) NSDate * lastUsed;

- (id) initWithTexture:(id<MTLTexture>)tex;
@end

@interface MTLPooledTextureHandle : NSObject
@property (readonly, assign) id<MTLTexture> texture;
@property (readonly) MTLRegion rect;
- (void) releaseTexture;
@end

// NOTE: owns all MTLTexture objects
@interface MTLTexturePool : NSObject
@property (readwrite, retain) id<MTLDevice> device;

- (id) initWithDevice:(id<MTLDevice>)device;
- (MTLPooledTextureHandle *) getTexture:(int)width height:(int)height format:(MTLPixelFormat)format;
@end

#endif /* MTLTexturePool_h_Included */
