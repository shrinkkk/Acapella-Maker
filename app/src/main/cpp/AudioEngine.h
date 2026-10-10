#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "SpscRing.h"
#include "WavWriter.h"

/** What a finished engine recording looked like. */
struct RecordingInfo {
    int64_t framesCaptured = 0;
    int32_t sampleRate = 0;
    double outputLatencyMs = -1;   // Oboe's estimate, -1 if unknown
    double inputLatencyMs = -1;    // Oboe's estimate, -1 if unknown
    float peak = 0.0f;             // 0..1
    int64_t overflowFrames = 0;    // frames dropped because the writer fell behind (should be 0)
};

/** A take's audio for playback: sample k plays at timeline frame (startFrame + k). */
struct PlaybackTrack {
    std::vector<float> samples;
    int64_t startFrame = 0;
    float gain = 1.0f;
};

/**
 * Low-latency audio engine.
 *
 * The output stream's frame counter is the app's master clock. Recording reads the mic inside
 * the output callback (full duplex), so every recorded sample has a fixed relationship to the
 * output timeline:
 *
 *   recorded sample k  <->  timeline frame (k - roundTripLatencyFrames)
 *
 * where timeline frame 0 is beat 1 of the take (recording and the beat restart happen in the
 * same callback). The round-trip latency is estimated from Oboe for now; the calibration step
 * will measure it properly using this exact code path.
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

    /** Opens the mic and starts writing a WAV at the next callback (with a beat restart). */
    bool startRecording(const std::string &wavPath);
    RecordingInfo stopRecording();

    // Playback of recorded takes on the shared timeline (timeline frame 0 = beat 1).
    // Tracks may only be added/cleared while playback is stopped.
    void clearTracks();
    void addTrack(std::vector<float> samples, int64_t startFrame, float gain);
    bool startPlayback();
    void stopPlayback();
    bool isPlaying() const { return mPlaying.load(); }
    /** Timeline frame of the next audio frame to be rendered. */
    int64_t playbackFrame() const { return mPlayFrame.load(); }
    int64_t playbackEndFrame() const { return mPlayEnd; }

    int64_t framesRendered() const { return mFramesRendered.load(); }
    int64_t beatsStarted() const { return mBeatsStarted.load(); }
    int32_t sampleRate() const { return mSampleRate.load(); }
    bool isRunning() const { return mRunning.load(); }
    /** Android AudioDeviceInfo ids of the devices actually in use (0 = unknown). */
    int32_t outputDeviceId() const { return mOutputDeviceId.load(); }
    int32_t inputDeviceId() const { return mInputDeviceId.load(); }
    bool isRecording() const { return mRecording.load() || mRecordRequested.load(); }

    // Oboe callbacks
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    bool openStream();
    bool openInputStream();
    void closeInputStream();
    void buildClicks(int32_t sampleRate);
    void captureInput();
    void writerLoop();

    std::mutex mLock;  // guards stream open/close (never taken on the audio thread)
    std::shared_ptr<oboe::AudioStream> mStream;
    std::shared_ptr<oboe::AudioStream> mInput;
    int32_t mChannelCount = 2;

    std::atomic<bool> mRunning{false};
    std::atomic<int32_t> mSampleRate{48000};
    std::atomic<int32_t> mOutputDeviceId{0};
    std::atomic<int32_t> mInputDeviceId{0};
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

    // Playback
    std::vector<PlaybackTrack> mTracks;   // only changed while !mPlaying
    std::atomic<bool> mPlaying{false};
    std::atomic<int64_t> mPlayFrame{0};
    int64_t mPlayEnd = 0;

    // Recording
    std::atomic<bool> mRecordRequested{false};
    std::atomic<bool> mRecording{false};
    std::atomic<int64_t> mFramesCaptured{0};
    std::atomic<int64_t> mOverflowFrames{0};
    std::atomic<float> mPeak{0.0f};
    std::vector<float> mScratch;      // allocated before recording; used on the audio thread
    SpscRing mRing{1 << 19};          // ~10 s at 48 kHz of slack for the writer thread
    WavWriter mWav;
    std::thread mWriterThread;
    std::atomic<bool> mWriterStop{false};
};
