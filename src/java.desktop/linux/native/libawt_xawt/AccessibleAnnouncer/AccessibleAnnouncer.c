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

#include "javax_swing_AccessibleAnnouncer.h"
#include "OrcaConf.h"
#include "jni_util.h"

/*
 * Class:     javax_swing_AccessibleAnnouncer
 * Method:    announce
 * Signature: (Ljavax/accessibility/Accessible;Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_javax_swing_AccessibleAnnouncer_announce(JNIEnv *env, jclass cls, jobject accessible, jstring str, jint priority)
{
    if (str != NULL)
    {
        const char *msg = JNU_GetStringPlatformChars(env, str, NULL);
        if (msg != NULL)
        {
			jobject conf = GetOrcaConf(env);
            if (conf != NULL &&
            (GetEnableSpeech(env, conf) > 0))
            {
                SPDConnection *connection = spd_open("Cli announcer", NULL, NULL, SPD_MODE_SINGLE);
                if (connection != NULL)
                {
                    SetSpeechConf(env, connection, conf);
                    int p = SPD_TEXT;
                    if (priority == javax_swing_AccessibleAnnouncer_ANNOUNCE_WITH_INTERRUPTING_CURRENT_OUTPUT)
                    {
                        p = SPD_MESSAGE;
                    }
                    spd_say(connection, p, msg);
                    (*env)->DeleteLocalRef(env, conf);
                    spd_close(connection);
                }
            }
            JNU_ReleaseStringPlatformChars(env, str, msg);
        }
    }
}
