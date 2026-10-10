#include "AudioEngine.h"

#include <android/log.h>
#include <cmath>

#define LOG_TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr double kPi = 3.14159265358979323846;
constexpr double kClickSeconds = 0.035;
}

bool AudioEngine::start() {
    std::lock_guard<std::mutex> lock(mLock);
    if (mRunning.load()) return true;
    if (!openStream()) return false;

    mFramesRendered.store(0);
    mRestartRequested.store(true);

    oboe::Result result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("requestStart failed: %s", oboe::convertToText(result));
        mStream->close();
        mStream.reset();
        return false;
    }
    mRunning.store(true);
    return true;
}

void AudioEngine::stop() {
    std::lock_guard<std::mutex> lock(mLock);
    mRunning.store(false);
    if (mStream) {
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }
}

void AudioEngine::setBpm(double bpm) {
    if (bpm < 20.0) bpm = 20.0;
    if (bpm > 300.0) bpm = 300.0;
    mBpm.store(bpm);
}

bool AudioEngine::openStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setUsage(oboe::Usage::Media)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setDataCallback(this)
            ->setErrorCallback(this);

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGE("openStream failed: %s", oboe::convertToText(result));
        mStream.reset();
        return false;
    }

    mChannelCount = mStream->getChannelCount();
    mSampleRate.store(mStream->getSampleRate());
    buildClicks(mStream->getSampleRate());

    LOGI("Output stream: %d Hz, %d ch, burst %d frames, sharing=%s, perf=%s",
         mStream->getSampleRate(), mChannelCount, mStream->getFramesPerBurst(),
         oboe::convertToText(mStream->getSharingMode()),
         oboe::convertToText(mStream->getPerformanceMode()));
    return true;
}

void AudioEngine::buildClicks(int32_t sampleRate) {
    // Short decaying sine "tick": higher pitch on beat 1 of the bar.
    auto make = [sampleRate](double freq) {
        auto length = static_cast<size_t>(kClickSeconds * sampleRate);
        std::vector<float> click(length);
        for (size_t i = 0; i < length; ++i) {
            double t = static_cast<double>(i) / sampleRate;
            double envelope = std::exp(-t * 120.0);
            click[i] = static_cast<float>(std::sin(2.0 * kPi * freq * t) * envelope);
        }
        return click;
    };
    mClickNormal = make(1000.0);
    mClickAccentSamples = make(1600.0);
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *stream, void *audioData,
                                                   int32_t numFrames) {
    auto *out = static_cast<float *>(audioData);
    const int32_t channels = mChannelCount;
    const double framesPerBeat = 60.0 * stream->getSampleRate() / mBpm.load();
    const bool metronomeOn = mMetronomeEnabled.load();
    const int beatsPerBar = mBeatsPerBar.load();
    const float volume = mVolume.load();
    int64_t frame = mFramesRendered.load();

    if (mRestartRequested.exchange(false)) {
        mNextBeatFrame = static_cast<double>(frame);
        mBeatIndex = 0;
        mClickPos = -1;
        mBeatsStarted.store(0);
    }

    for (int32_t i = 0; i < numFrames; ++i, ++frame) {
        if (static_cast<double>(frame) >= mNextBeatFrame) {
            mClickAccent = (mBeatIndex % beatsPerBar) == 0;
            mClickPos = 0;
            mBeatIndex++;
            mBeatsStarted.store(mBeatIndex);
            // Accumulate as double so the tempo never drifts from rounding.
            mNextBeatFrame += framesPerBeat;
        }

        float sample = 0.0f;
        if (metronomeOn && mClickPos >= 0) {
            const auto &click = mClickAccent ? mClickAccentSamples : mClickNormal;
            if (static_cast<size_t>(mClickPos) < click.size()) {
                sample = click[mClickPos] * volume;
                mClickPos++;
            } else {
                mClickPos = -1;
            }
        }

        for (int32_t c = 0; c < channels; ++c) {
            out[i * channels + c] = sample;
        }
    }

    mFramesRendered.store(frame);
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream * /*stream*/, oboe::Result error) {
    // Typically the output device changed (earphones plugged/unplugged). Reopen on the new device.
    LOGI("Stream closed (%s), restarting", oboe::convertToText(error));
    std::lock_guard<std::mutex> lock(mLock);
    if (!mRunning.load()) return;
    mStream.reset();
    if (openStream()) {
        mRestartRequested.store(true);
        mStream->requestStart();
    } else {
        mRunning.store(false);
    }
}
