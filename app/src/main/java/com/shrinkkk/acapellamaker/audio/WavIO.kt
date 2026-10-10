package com.shrinkkk.acapellamaker.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads the engine's own WAV format: 44-byte header, mono 16-bit PCM. */
fun readEngineWav(file: File): FloatArray {
    RandomAccessFile(file, "r").use { raf ->
        val dataBytes = (raf.length() - 44).toInt().coerceAtLeast(0)
        val bytes = ByteArray(dataBytes)
        raf.seek(44)
        raf.readFully(bytes)
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(shorts.remaining()) { shorts.get(it) / 32768f }
    }
}
