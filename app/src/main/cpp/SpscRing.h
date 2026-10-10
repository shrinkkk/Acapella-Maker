#pragma once

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <vector>

/**
 * Single-producer / single-consumer ring buffer of floats.
 * Producer = audio callback thread (must never block), consumer = file writer thread.
 */
class SpscRing {
public:
    explicit SpscRing(size_t capacity) : mBuffer(capacity), mCapacity(capacity) {}

    size_t write(const float *data, size_t count) {
        const size_t w = mWrite.load(std::memory_order_relaxed);
        const size_t r = mRead.load(std::memory_order_acquire);
        const size_t space = mCapacity - (w - r);
        const size_t n = std::min(count, space);
        for (size_t i = 0; i < n; ++i) mBuffer[(w + i) % mCapacity] = data[i];
        mWrite.store(w + n, std::memory_order_release);
        return n;
    }

    size_t read(float *data, size_t count) {
        const size_t r = mRead.load(std::memory_order_relaxed);
        const size_t w = mWrite.load(std::memory_order_acquire);
        const size_t n = std::min(count, w - r);
        for (size_t i = 0; i < n; ++i) data[i] = mBuffer[(r + i) % mCapacity];
        mRead.store(r + n, std::memory_order_release);
        return n;
    }

    /** Only call while neither thread is using the buffer. */
    void reset() {
        mWrite.store(0);
        mRead.store(0);
    }

private:
    std::vector<float> mBuffer;
    const size_t mCapacity;
    std::atomic<size_t> mWrite{0};
    std::atomic<size_t> mRead{0};
};
