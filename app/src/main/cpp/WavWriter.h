#pragma once

#include <cstdint>
#include <cstdio>
#include <string>

/** Minimal mono 16-bit PCM WAV writer. Not real-time safe: use from the writer thread only. */
class WavWriter {
public:
    bool open(const std::string &path, int32_t sampleRate);
    void write(const float *samples, size_t count);
    void close();  // fills in the header sizes
    ~WavWriter() { close(); }

private:
    FILE *mFile = nullptr;
    int32_t mSampleRate = 48000;
    uint32_t mDataBytes = 0;
};
