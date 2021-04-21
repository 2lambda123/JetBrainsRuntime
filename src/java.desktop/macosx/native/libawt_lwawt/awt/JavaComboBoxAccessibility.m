// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#import "JavaComboBoxAccessibility.h"
#import "JavaAccessibilityAction.h"
#import "JavaAccessibilityUtilities.h"
#import "ThreadUtilities.h"
#import <JavaNativeFoundation/JavaNativeFoundation.h>

static JNF_STATIC_MEMBER_CACHE(sjm_getAccessibleName, sjc_CAccessibility, "getAccessibleName", "(Ljavax/accessibility/Accessible;Ljava/awt/Component;)Ljava/lang/String;");

static const char* ACCESSIBLE_JCOMBOBOX_NAME = "javax.swing.JComboBox$AccessibleJComboBox";

@implementation JavaComboBoxAccessibility

- (NSString *)getPlatformAxElementClassName {
    return @"PlatformAxComboBox";
}

- (NSString *)accessibleSelectedText {
    JNIEnv *env = [ThreadUtilities getJNIEnv];
    JNFClassInfo clsInfo;
    clsInfo.name = ACCESSIBLE_JCOMBOBOX_NAME;
    clsInfo.cls = (*env)->GetObjectClass(env, [self axContextWithEnv:env]);
    JNF_MEMBER_CACHE(jm_getAccessibleSelection, clsInfo, "getAccessibleSelection", "(I)Ljavax/accessibility/Accessible;");
    jobject axSelectedChild = JNFCallObjectMethod(env, [self axContextWithEnv:env], jm_getAccessibleSelection, 0);
    if (axSelectedChild == NULL) {
        return nil;
    }
    jobject childName = JNFCallStaticObjectMethod(env, sjm_getAccessibleName, axSelectedChild, fComponent);
    if (childName == NULL) {
        (*env)->DeleteLocalRef(env, axSelectedChild);
        return nil;
    }
    NSString *selectedText = JNFObjectToString(env, childName);
    (*env)->DeleteLocalRef(env, axSelectedChild);
    (*env)->DeleteLocalRef(env, childName);
    return selectedText;
}

@end

@implementation PlatformAxComboBox

- (id)accessibilityValue {
    return [(JavaComboBoxAccessibility *)[self javaBase] accessibleSelectedText];
}

@end
