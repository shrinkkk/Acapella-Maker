#include <jni.h>
#include <memory>
#include "AudioEngine.h"

namespace {
std::unique_ptr<AudioEngine> gEngine;

AudioEngine &engine() {
    if (!gEngine) gEngine = std::make_unique<AudioEngine>();
    return *gEngine;
}
}  // namespace

#define JNI_FN(name) Java_com_shrinkkk_acapellamaker_audio_AudioEngine_##name

extern "C" {

JNIEXPORT jboolean JNICALL JNI_FN(nativeStart)(JNIEnv *, jobject) {
    return engine().start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(nativeStop)(JNIEnv *, jobject) {
    engine().stop();
}

JNIEXPORT void JNICALL JNI_FN(nativeSetMetronomeEnabled)(JNIEnv *, jobject, jboolean enabled) {
    engine().setMetronomeEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL JNI_FN(nativeSetBpm)(JNIEnv *, jobject, jdouble bpm) {
    engine().setBpm(bpm);
}

JNIEXPORT void JNICALL JNI_FN(nativeSetBeatsPerBar)(JNIEnv *, jobject, jint beats) {
    engine().setBeatsPerBar(beats);
}

JNIEXPORT void JNICALL JNI_FN(nativeSetMetronomeVolume)(JNIEnv *, jobject, jfloat volume) {
    engine().setMetronomeVolume(volume);
}

JNIEXPORT void JNICALL JNI_FN(nativeRestartBeat)(JNIEnv *, jobject) {
    engine().restartBeat();
}

JNIEXPORT jlong JNICALL JNI_FN(nativeBeatsStarted)(JNIEnv *, jobject) {
    return static_cast<jlong>(engine().beatsStarted());
}

JNIEXPORT jlong JNICALL JNI_FN(nativeFramesRendered)(JNIEnv *, jobject) {
    return static_cast<jlong>(engine().framesRendered());
}

JNIEXPORT jint JNICALL JNI_FN(nativeSampleRate)(JNIEnv *, jobject) {
    return engine().sampleRate();
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeIsRunning)(JNIEnv *, jobject) {
    return engine().isRunning() ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
