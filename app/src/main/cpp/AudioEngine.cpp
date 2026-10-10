#include "AudioEngine.h"

#include <android/log.h>
#include <chrono>
#include <cmath>

#define LOG_TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr double kPi = 3.14159265358979323846;
constexpr double kClickSeconds = 0.035;
constexpr int32_t kScratchFrames = 4096;

double latencyMs(const std::shared_ptr<oboe::AudioStream> &stream) {
    if (!stream) return -1;
    auto result = stream->calculateLatencyMillis();
    return result ? result.value() : -1;
}
}  // namespace

// ---------------------------------------------------------------- output stream

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
    if (isRecording()) stopRecording();
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
    mOutputDeviceId.store(mStream->getDeviceId());
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

// ---------------------------------------------------------------- input / recording

bool AudioEngine::openInputStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            // Shared: the camera may also be capturing the mic for the video file.
            ->setSharingMode(oboe::SharingMode::Shared)
            // Low-latency preset meant for live music performance.
            ->setInputPreset(oboe::InputPreset::VoicePerformance)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(mSampleRate.load())
            ->setFormatConversionAllowed(true)
            ->setChannelConversionAllowed(true)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);
    // No callback: the output callback reads from this stream (non-blocking).

    oboe::Result result = builder.openStream(mInput);
    if (result != oboe::Result::OK) {
        LOGE("open input failed: %s", oboe::convertToText(result));
        mInput.reset();
        return false;
    }
    result = mInput->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("start input failed: %s", oboe::convertToText(result));
        mInput->close();
        mInput.reset();
        return false;
    }
    mInputDeviceId.store(mInput->getDeviceId());
    LOGI("Input stream: %d Hz, %d ch, sharing=%s, perf=%s",
         mInput->getSampleRate(), mInput->getChannelCount(),
         oboe::convertToText(mInput->getSharingMode()),
         oboe::convertToText(mInput->getPerformanceMode()));
    return true;
}

void AudioEngine::closeInputStream() {
    if (mInput) {
        mInput->requestStop();
        mInput->close();
        mInput.reset();
    }
}

bool AudioEngine::startRecording(const std::string &wavPath) {
    std::lock_guard<std::mutex> lock(mLock);
    if (!mRunning.load() || !mStream) {
        LOGE("startRecording: engine not running");
        return false;
    }
    if (mRecording.load() || mRecordRequested.load()) return false;

    if (!mWav.open(wavPath, mSampleRate.load())) {
        LOGE("startRecording: can't open %s", wavPath.c_str());
        return false;
    }
    if (!openInputStream()) {
        mWav.close();
        return false;
    }

    mScratch.assign(kScratchFrames, 0.0f);
    mRing.reset();
    mFramesCaptured.store(0);
    mOverflowFrames.store(0);
    mPeak.store(0.0f);

    mWriterStop.store(false);
    mWriterThread = std::thread(&AudioEngine::writerLoop, this);

    // Both take effect at the start of the same callback: beat 1 == first recorded frame.
    mRestartRequested.store(true);
    mRecordRequested.store(true);
    return true;
}

RecordingInfo AudioEngine::stopRecording() {
    RecordingInfo info;
    std::lock_guard<std::mutex> lock(mLock);
    if (!mRecording.load() && !mRecordRequested.load()) return info;

    info.outputLatencyMs = latencyMs(mStream);
    info.inputLatencyMs = latencyMs(mInput);

    mRecordRequested.store(false);
    mRecording.store(false);
    // Give an in-flight callback time to finish touching the input stream / ring.
    std::this_thread::sleep_for(std::chrono::milliseconds(50));

    mWriterStop.store(true);
    if (mWriterThread.joinable()) mWriterThread.join();
    mWav.close();
    closeInputStream();

    info.framesCaptured = mFramesCaptured.load();
    info.sampleRate = mSampleRate.load();
    info.peak = mPeak.load();
    info.overflowFrames = mOverflowFrames.load();
    LOGI("Recording done: %lld frames, peak %.3f, latency out %.1f ms / in %.1f ms, overflow %lld",
         static_cast<long long>(info.framesCaptured), info.peak, info.outputLatencyMs,
         info.inputLatencyMs, static_cast<long long>(info.overflowFrames));
    return info;
}

void AudioEngine::writerLoop() {
    std::vector<float> chunk(8192);
    while (true) {
        size_t n;
        while ((n = mRing.read(chunk.data(), chunk.size())) > 0) mWav.write(chunk.data(), n);
        if (mWriterStop.load()) {
            // Final drain after the producer has stopped.
            while ((n = mRing.read(chunk.data(), chunk.size())) > 0) mWav.write(chunk.data(), n);
            break;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

/** Audio thread: move whatever mic input is available into the ring buffer. */
void AudioEngine::captureInput() {
    if (!mInput) return;
    float peak = mPeak.load(std::memory_order_relaxed);
    for (int guard = 0; guard < 16; ++guard) {
        auto result = mInput->read(mScratch.data(), kScratchFrames, 0);
        if (!result) break;
        const int32_t n = result.value();
        if (n <= 0) break;
        for (int32_t i = 0; i < n; ++i) peak = std::max(peak, std::fabs(mScratch[i]));
        const size_t written = mRing.write(mScratch.data(), static_cast<size_t>(n));
        if (written < static_cast<size_t>(n)) {
            mOverflowFrames.fetch_add(static_cast<int64_t>(n) - static_cast<int64_t>(written));
        }
        mFramesCaptured.fetch_add(n);
        if (n < kScratchFrames) break;
    }
    mPeak.store(peak, std::memory_order_relaxed);
}

// ---------------------------------------------------------------- audio callback

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

    if (mRecordRequested.exchange(false)) {
        // Throw away mic audio captured before this moment, so recorded sample 0 lines up
        // with this callback (the same callback where beat 1 starts).
        if (mInput) {
            for (int guard = 0; guard < 64; ++guard) {
                auto result = mInput->read(mScratch.data(), kScratchFrames, 0);
                if (!result || result.value() <= 0) break;
            }
        }
        mRecording.store(true);
    } else if (mRecording.load()) {
        captureInput();
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

void AudioEngine::onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) {
    if (stream != mStream.get()) return;  // only the output stream has callbacks
    // Typically the output device changed (earphones plugged/unplugged). Reopen on the new device.
    LOGI("Output stream closed (%s), restarting", oboe::convertToText(error));
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
