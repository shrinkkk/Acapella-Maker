#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <vector>

/**
 * Low-latency audio engine.
 *
 * The output stream's frame counter is the app's master clock: everything (metronome clicks now,
 * backing takes and recorded audio later) is positioned in frames on this one timeline.
 */
class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    bool start();
    void stop();

    void setMetronomeEnabled(bool enabled) { mMetronomeEnabled.store(enabled); }
    void setBpm(double bpm);
    void setBeatsPerBar(int beats) { mBeatsPerBar.store(beats > 0 ? beats : 4); }
    void setMetronomeVolume(float volume) { mVolume.store(volume); }

    /** Starts the beat grid again from beat 1 at the next audio callback. */
    void restartBeat() { mRestartRequested.store(true); }

    int64_t framesRendered() const { return mFramesRendered.load(); }
    int64_t beatsStarted() const { return mBeatsStarted.load(); }
    int32_t sampleRate() const { return mSampleRate.load(); }
    bool isRunning() const { return mRunning.load(); }

    // Oboe callbacks
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    bool openStream();
    void buildClicks(int32_t sampleRate);

    std::mutex mLock;  // guards stream open/close (not used on the audio thread)
    std::shared_ptr<oboe::AudioStream> mStream;
    int32_t mChannelCount = 2;

    std::atomic<bool> mRunning{false};
    std::atomic<int32_t> mSampleRate{48000};
    std::atomic<int64_t> mFramesRendered{0};

    // Metronome settings (written by UI thread, read by audio thread)
    std::atomic<bool> mMetronomeEnabled{false};
    std::atomic<double> mBpm{100.0};
    std::atomic<int> mBeatsPerBar{4};
    std::atomic<float> mVolume{0.8f};
    std::atomic<bool> mRestartRequested{false};

    // Metronome state (audio thread only, except mBeatsStarted)
    double mNextBeatFrame = 0.0;
    int64_t mBeatIndex = 0;
    int32_t mClickPos = -1;
    bool mClickAccent = false;
    std::atomic<int64_t> mBeatsStarted{0};

    std::vector<float> mClickNormal;
    std::vector<float> mClickAccentSamples;
};
