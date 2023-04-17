/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/types.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>

#include <jni.h>
#include <netinet/tcp.h>
#include <netinet/in.h>
#include "jni_util.h"
#include "jdk_net_AIXSocketOptions.h"


static void handleError(JNIEnv *env, jint rv, const char *errmsg) {
    if (rv < 0) {
        if (errno == ENOPROTOOPT) {
            JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                    "unsupported socket option");
        } else {
            JNU_ThrowByNameWithLastError(env, "java/net/SocketException", errmsg);
        }
    }
}

static jint socketOptionSupported(jint level, jint optname) {
    jint one = 1;
    jint rv, s;
    socklen_t sz = sizeof (one);
    /* First try IPv6; fall back to IPv4. */
    s = socket(PF_INET6, SOCK_STREAM, IPPROTO_TCP);
    if (s < 0) {
        if (errno == EPFNOSUPPORT || errno == EAFNOSUPPORT) {
            s = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
        }
        if (s < 0) {
            return 0;
        }
    }
    rv = getsockopt(s, level, optname, (void *) &one, &sz);
    if (rv != 0 && errno == ENOPROTOOPT) {
        rv = 0;
    } else {
        rv = 1;
    }
    close(s);
    return rv;
}

/*
 * Declare library specific JNI_Onload entry if static build
 */
DEF_STATIC_JNI_OnLoad

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    setQuickAck
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_AIXSocketOptions_setQuickAck0
(JNIEnv *env, jobject unused, jint fd, jboolean on) {
    int optval;
    int rv;
    optval = (on ? 1 : 0);
    rv = setsockopt(fd, SOL_SOCKET, TCP_NODELAYACK, &optval, sizeof (optval));
    handleError(env, rv, "set option TCP_NODELAYACK failed");
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    getQuickAck
 * Signature: (I)Z;
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_AIXSocketOptions_getQuickAck0
(JNIEnv *env, jobject unused, jint fd) {
    int on;
    socklen_t sz = sizeof (on);
    int rv = getsockopt(fd, SOL_SOCKET, TCP_NODELAYACK, &on, &sz);
    handleError(env, rv, "get option TCP_NODELAYACK failed");
    return on != 0;
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    quickAckSupported
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_AIXSocketOptions_quickAckSupported0
(JNIEnv *env, jobject unused) {
    return socketOptionSupported(SOL_SOCKET, TCP_NODELAYACK);
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    getSoPeerCred0
 * Signature: (I)L
 */
JNIEXPORT jlong JNICALL Java_jdk_net_AIXSocketOptions_getSoPeerCred0
  (JNIEnv *env, jclass clazz, jint fd) {

    int rv;
    struct peercred_struct cred_info;
    socklen_t len = sizeof(cred_info);

    if ((rv=getsockopt(fd, SOL_SOCKET, SO_PEERID, &cred_info, &len)) < 0) {
        handleError(env, rv, "get SO_PEERID failed");
    } else {
        if ((int)cred_info.euid == -1) {
            handleError(env, -1, "get SO_PEERID failed");
            cred_info.euid = cred_info.egid = -1;
        }
    }
    return (((jlong)cred_info.euid) << 32) | (cred_info.egid & 0xffffffffL);
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    keepAliveOptionsSupported0
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_AIXSocketOptions_keepAliveOptionsSupported0
(JNIEnv *env, jobject unused) {
    return socketOptionSupported(IPPROTO_TCP, TCP_KEEPIDLE) && socketOptionSupported(IPPROTO_TCP, TCP_KEEPCNT)
            && socketOptionSupported(IPPROTO_TCP, TCP_KEEPINTVL);
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    setTcpkeepAliveProbes0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_AIXSocketOptions_setTcpkeepAliveProbes0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, sizeof (optval));
    handleError(env, rv, "set option TCP_KEEPCNT failed");
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    setTcpKeepAliveTime0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_AIXSocketOptions_setTcpKeepAliveTime0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPIDLE, &optval, sizeof (optval));
    handleError(env, rv, "set option TCP_KEEPIDLE failed");
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    setTcpKeepAliveIntvl0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_jdk_net_AIXSocketOptions_setTcpKeepAliveIntvl0
(JNIEnv *env, jobject unused, jint fd, jint optval) {
    jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, sizeof (optval));
    handleError(env, rv, "set option TCP_KEEPINTVL failed");
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    getTcpkeepAliveProbes0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_AIXSocketOptions_getTcpkeepAliveProbes0
(JNIEnv *env, jobject unused, jint fd) {
    jint optval, rv;
    socklen_t sz = sizeof (optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPCNT failed");
    return optval;
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    getTcpKeepAliveTime0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_AIXSocketOptions_getTcpKeepAliveTime0
(JNIEnv *env, jobject unused, jint fd) {
    jint optval, rv;
    socklen_t sz = sizeof (optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPIDLE, &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPIDLE failed");
    return optval;
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    getTcpKeepAliveIntvl0
 * Signature: (I)I;
 */
JNIEXPORT jint JNICALL Java_jdk_net_AIXSocketOptions_getTcpKeepAliveIntvl0
(JNIEnv *env, jobject unused, jint fd) {
    jint optval, rv;
    socklen_t sz = sizeof (optval);
    rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, &sz);
    handleError(env, rv, "get option TCP_KEEPINTVL failed");
    return optval;
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    setIpDontFragment0
 * Signature: (IZZ)V
 */
JNIEXPORT void JNICALL Java_jdk_net_AIXSocketOptions_setIpDontFragment0
(JNIEnv *env, jobject unused, jint fd, jboolean optval, jboolean isIPv6) {
    jint rv, optsetting;

    //optsetting = optval ? IP_PMTUDISC_DO/IP_DONTFRAG : IP_PMTUDISC_DONT;
    optsetting = optval ? 1 : 0;
    if (!isIPv6) {
        rv = setsockopt(fd, IPPROTO_IP, IP_DONTFRAG, &optsetting, sizeof (optsetting));
    } else {
        rv = setsockopt(fd, IPPROTO_IPV6, IPV6_DONTFRAG, &optsetting, sizeof (optsetting));
    }
    handleError(env, rv, "set option IP_DONTFRAGMENT failed");
}

/*
 * Class:     jdk_net_AIXSocketOptions
 * Method:    getIpDontFragment0
 * Signature: (IZ)Z;
 */
JNIEXPORT jboolean JNICALL Java_jdk_net_AIXSocketOptions_getIpDontFragment0
(JNIEnv *env, jobject unused, jint fd, jboolean isIPv6) {
    jint optlevel, optname, optval, rv;

    if (!isIPv6) {
        optlevel = IPPROTO_IP;
        optname = IP_DONTFRAG;
    } else {
        optlevel = IPPROTO_IPV6;
        optname = IPV6_DONTFRAG;
    }
    socklen_t sz = sizeof(optval);
    rv = getsockopt(fd, optlevel, optname, &optval, &sz);
    handleError(env, rv, "get option IP_DONTFRAGMENT failed");
    return optval > 0 ? JNI_TRUE : JNI_FALSE;
}
