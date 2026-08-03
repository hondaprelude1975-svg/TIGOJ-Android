#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_tigoj_aipro_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject) {
    return env->NewStringUTF("TIGOJ Native OK");
}
