#include "WavWriter.h"

#include <algorithm>
#include <cmath>
#include <vector>

namespace {
void put16(FILE *f, uint16_t v) { fwrite(&v, 2, 1, f); }
void put32(FILE *f, uint32_t v) { fwrite(&v, 4, 1, f); }

void writeHeader(FILE *f, int32_t sampleRate, uint32_t dataBytes) {
    fwrite("RIFF", 1, 4, f);
    put32(f, 36 + dataBytes);
    fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f);
    put32(f, 16);            // fmt chunk size
    put16(f, 1);             // PCM
    put16(f, 1);             // mono
    put32(f, static_cast<uint32_t>(sampleRate));
    put32(f, static_cast<uint32_t>(sampleRate) * 2);  // byte rate
    put16(f, 2);             // block align
    put16(f, 16);            // bits per sample
    fwrite("data", 1, 4, f);
    put32(f, dataBytes);
}
}  // namespace

bool WavWriter::open(const std::string &path, int32_t sampleRate) {
    close();
    mFile = fopen(path.c_str(), "wb");
    if (!mFile) return false;
    mSampleRate = sampleRate;
    mDataBytes = 0;
    writeHeader(mFile, mSampleRate, 0);  // placeholder sizes
    return true;
}

void WavWriter::write(const float *samples, size_t count) {
    if (!mFile || count == 0) return;
    std::vector<int16_t> pcm(count);
    for (size_t i = 0; i < count; ++i) {
        float s = std::max(-1.0f, std::min(1.0f, samples[i]));
        pcm[i] = static_cast<int16_t>(std::lrint(s * 32767.0f));
    }
    fwrite(pcm.data(), sizeof(int16_t), count, mFile);
    mDataBytes += static_cast<uint32_t>(count * sizeof(int16_t));
}

void WavWriter::close() {
    if (!mFile) return;
    fseek(mFile, 0, SEEK_SET);
    writeHeader(mFile, mSampleRate, mDataBytes);
    fclose(mFile);
    mFile = nullptr;
}
