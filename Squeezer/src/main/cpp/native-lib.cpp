//
// Created by SamMiler on 2022/8/19.
//

#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_uk_org_ngo_squeezer_ConnectActivity_stringFromJNI (
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}