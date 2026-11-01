#include <jni.h>
#include "kindle_core.h"
JNIEXPORT jint JNICALL
Java_com_huangder_lumibooks_util_kindle_KindleNative_reconstruct(
        JNIEnv *env, jobject receiver, jstring source, jstring output) {
    (void)receiver;
    if (!source || !output) return 1;
    const char *s = (*env)->GetStringUTFChars(env, source, NULL);
    if (!s) return 5;
    const char *o = (*env)->GetStringUTFChars(env, output, NULL);
    if (!o) { (*env)->ReleaseStringUTFChars(env, source, s); return 5; }
    int result = lumi_kindle_reconstruct(s, o);
    (*env)->ReleaseStringUTFChars(env, output, o);
    (*env)->ReleaseStringUTFChars(env, source, s);
    return result;
}
