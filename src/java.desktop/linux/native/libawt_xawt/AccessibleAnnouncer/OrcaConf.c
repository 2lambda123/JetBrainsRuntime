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

#include "OrcaConf.h"
#include "AccessibleAnnouncerJNIUtils.h"
#include "jni_util.h"

static jclass jc_AccessibleAnnouncerUtilities = NULL;
static jmethodID jsm_getOrcaConf = NULL;
static jmethodID jsm_getSpeechServerInfo = NULL;
static jmethodID jsm_getGain = NULL;
static jmethodID jsm_getVariant = NULL;
static jmethodID jsm_getDialect = NULL;
static jmethodID jsm_getLang = NULL;
static jmethodID jsm_getName = NULL;
static jmethodID jsm_getAveragePitch = NULL;
static jmethodID jsm_getRate = NULL;
static jmethodID jsm_getEstablished = NULL;
static jmethodID jsm_getActiveProfile = NULL;
static jmethodID jsm_getVerbalizePunctuationStyle = NULL;
static jmethodID jsm_getOnlySpeakDisplayedText = NULL;
static jmethodID jsm_getEnableSpeech = NULL;

void SetSpeechConf(JNIEnv *env, SPDConnection *connection, jobject conf)
{
SetOutputModule(env, connection, conf);
SetSynthesisVoice(env, connection, conf);
set_language(env, connection, conf);
SetPunctuation(env, connection, conf);
SetVoiceRate(env, connection, conf);
SetVoicePitch(env, connection, conf);
SetVolume(env, connection, conf);
}

void SetVolume(JNIEnv *env, SPDConnection *connection, jobject conf)
{
    GET_getGain();
    jdouble gain = (*env)->CallStaticDoubleMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getGain, conf);
    JNU_CHECK_EXCEPTION(env);
    if (gain >= 0)
    {
        int volume = gain * 20 - 100;
        spd_set_volume(connection, volume);
    }
}

void SetVoiceRate(JNIEnv *env, SPDConnection *connection, jobject conf)
{
    GET_getRate();
    jdouble rate = (*env)->CallStaticDoubleMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getRate, conf);
    JNU_CHECK_EXCEPTION(env);
    if (rate >= 0)
    {
        int iRate = rate * 2 - 100;
        spd_set_voice_rate(connection, iRate);
    }
}

void SetPunctuation(JNIEnv *env, SPDConnection *connection, jobject conf)
{
    GET_getVerbalizePunctuationStyle();
    jint punctuation = (*env)->CallStaticIntMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getVerbalizePunctuationStyle, conf);
    JNU_CHECK_EXCEPTION(env);
    if (punctuation >= 0)
    {
        spd_set_punctuation(connection, punctuation);
    }
}

void SetVoicePitch(JNIEnv *env, SPDConnection *connection, jobject conf)
{
    GET_getAveragePitch();
    jdouble pitch = (*env)->CallStaticDoubleMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getAveragePitch, conf);
    JNU_CHECK_EXCEPTION(env);
    if (pitch >= 0)
    {
        int iPitch = pitch * 20 - 100;
        spd_set_voice_pitch(connection, iPitch);
    }
}

void SetOutputModule(JNIEnv *env, SPDConnection *connection, jobject conf)
{
    GET_getSpeechServerInfo();
    jobject jStr = (*env)->CallStaticObjectMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getSpeechServerInfo, conf);
    JNU_CHECK_EXCEPTION(env);
    if (jStr != NULL)
    {
        const char *sintName = JNU_GetStringPlatformChars(env, jStr, NULL);
        if (sintName != NULL)
        {
            spd_set_output_module(connection, sintName);
            JNU_ReleaseStringPlatformChars(env, jStr, sintName);
        }
        (*env)->DeleteLocalRef(env, jStr);
    }
}

void set_language(JNIEnv *env, SPDConnection *connection, jobject conf)
{
    GET_getLang();
    jobject jStr = (*env)->CallStaticObjectMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getLang, conf);
    JNU_CHECK_EXCEPTION(env);
    if (jStr != NULL)
    {
        const char *lang = JNU_GetStringPlatformChars(env, jStr, NULL);
        if (lang != NULL)
        {
            spd_set_language(connection, lang);
            JNU_ReleaseStringPlatformChars(env, jStr, lang);
        }
        (*env)->DeleteLocalRef(env, jStr);
    }
}

int GetEnableSpeech(JNIEnv *env, jobject conf)
{
    GET_getEnableSpeech(-1);
    int es = (*env)->CallStaticBooleanMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getEnableSpeech, conf);
    JNU_CHECK_EXCEPTION_RETURN(env, -1);
    return es;
}

int GetOnlySpeakDisplayedText(JNIEnv *env, jobject conf)
{
    GET_getOnlySpeakDisplayedText(-1);
    int osd = (*env)->CallStaticBooleanMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getOnlySpeakDisplayedText, conf);
    JNU_CHECK_EXCEPTION_RETURN(env, -1);
    return osd;
}

int GetEstablished(JNIEnv *env, jobject conf)
{
    GET_getEstablished(-1);
    int e = (*env)->CallStaticBooleanMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getOnlySpeakDisplayedText, conf);
    JNU_CHECK_EXCEPTION_RETURN(env, -1);
    return e;
}

void SetSynthesisVoice(JNIEnv *env, SPDConnection *connection, jobject conf)
{
    GET_getName();
    jobject jStr = (*env)->CallStaticObjectMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getName, conf);
    JNU_CHECK_EXCEPTION(env);
    if (jStr != NULL)
    {
        const char *voiceName = JNU_GetStringPlatformChars(env, jStr, NULL);
        if (voiceName != NULL)
        {
spd_set_synthesis_voice(connection, voiceName);
JNU_ReleaseStringPlatformChars(env, jStr, voiceName);
        }
        (*env)->DeleteLocalRef(env, jStr);
    }
}

jobject GetOrcaConf(JNIEnv *env)
{
    GET_getOrcaConfReturn(NULL);
    jobject o = (*env)->CallStaticObjectMethod(env, jc_AccessibleAnnouncerUtilities, jsm_getOrcaConf);
    JNU_CHECK_EXCEPTION_RETURN(env, NULL);
return o;
}
