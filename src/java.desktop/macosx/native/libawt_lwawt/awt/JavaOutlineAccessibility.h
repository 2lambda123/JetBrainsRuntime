// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#import "JavaListAccessibility.h"

// This is a tree representation.. Look This: https://developer.apple.com/documentation/appkit/nsoutlineview

@interface JavaOutlineAccessibility : JavaListAccessibility
@end

@interface PlatformAxOutline : PlatformAxList <NSAccessibilityOutline>
@end
