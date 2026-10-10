#include <jni.h>
#include <memory>
#include <string>
#include <vector>
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

JNIEXPORT jboolean JNICALL JNI_FN(nativeStartRecording)(JNIEnv *env, jobject, jstring path) {
    const char *chars = env->GetStringUTFChars(path, nullptr);
    std::string wavPath(chars);
    env->ReleaseStringUTFChars(path, chars);
    return engine().startRecording(wavPath) ? JNI_TRUE : JNI_FALSE;
}

/** [framesCaptured, sampleRate, outputLatencyMs, inputLatencyMs, peak, overflowFrames] */
JNIEXPORT jdoubleArray JNICALL JNI_FN(nativeStopRecording)(JNIEnv *env, jobject) {
    RecordingInfo info = engine().stopRecording();
    jdouble values[6] = {
            static_cast<jdouble>(info.framesCaptured),
            static_cast<jdouble>(info.sampleRate),
            info.outputLatencyMs,
            info.inputLatencyMs,
            static_cast<jdouble>(info.peak),
            static_cast<jdouble>(info.overflowFrames),
    };
    jdoubleArray result = env->NewDoubleArray(6);
    env->SetDoubleArrayRegion(result, 0, 6, values);
    return result;
}

JNIEXPORT void JNICALL JNI_FN(nativeClearTracks)(JNIEnv *, jobject) {
    engine().clearTracks();
}

JNIEXPORT void JNICALL JNI_FN(nativeAddTrack)(JNIEnv *env, jobject, jfloatArray samples,
                                               jlong startFrame, jint slot) {
    const jsize length = env->GetArrayLength(samples);
    std::vector<float> copy(static_cast<size_t>(length));
    env->GetFloatArrayRegion(samples, 0, length, copy.data());
    engine().addTrack(std::move(copy), static_cast<int64_t>(startFrame), slot);
}

JNIEXPORT void JNICALL JNI_FN(nativeSetSlotGain)(JNIEnv *, jobject, jint slot, jfloat gain) {
    engine().setSlotGain(slot, gain);
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeStartPlayback)(JNIEnv *, jobject, jboolean loop) {
    return engine().startPlayback(loop == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(nativeStopPlayback)(JNIEnv *, jobject) {
    engine().stopPlayback();
}

JNIEXPORT jlong JNICALL JNI_FN(nativePlaybackFrame)(JNIEnv *, jobject) {
    return static_cast<jlong>(engine().playbackFrame());
}

JNIEXPORT jlong JNICALL JNI_FN(nativePlaybackEndFrame)(JNIEnv *, jobject) {
    return static_cast<jlong>(engine().playbackEndFrame());
}

JNIEXPORT jint JNICALL JNI_FN(nativeOutputDeviceId)(JNIEnv *, jobject) {
    return engine().outputDeviceId();
}

JNIEXPORT jint JNICALL JNI_FN(nativeInputDeviceId)(JNIEnv *, jobject) {
    return engine().inputDeviceId();
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeIsRunning)(JNIEnv *, jobject) {
    return engine().isRunning() ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
