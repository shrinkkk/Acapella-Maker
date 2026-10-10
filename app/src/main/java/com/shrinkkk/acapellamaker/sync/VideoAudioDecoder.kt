package com.shrinkkk.acapellamaker.sync

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/** Mono audio decoded from a video file, plus the presentation time of its first sample. */
class DecodedAudio(val samples: FloatArray, val sampleRate: Int, val firstSampleTimeUs: Long)

/**
 * Decodes the audio track of [file] (the camera's own recording) to mono floats.
 * Stops after [maxSeconds] since alignment only needs the start.
 */
fun decodeVideoAudio(file: File, maxSeconds: Double = 20.0): DecodedAudio? {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var isFloat = false

        val decoder = MediaCodec.createDecoderByType(mime)
        codec = decoder
        decoder.configure(format, null, null, 0)
        decoder.start()

        var out = FloatArray(sampleRate * 10)
        var count = 0
        var firstTimeUs = Long.MIN_VALUE
        val info = MediaCodec.BufferInfo()
        var inputDone = false

        while (true) {
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val f = decoder.outputFormat
                sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            } else if (outIndex >= 0) {
                if (info.size > 0) {
                    if (firstTimeUs == Long.MIN_VALUE) firstTimeUs = info.presentationTimeUs
                    val buffer = decoder.getOutputBuffer(outIndex)!!
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    buffer.order(ByteOrder.nativeOrder())
                    val frames: Int
                    val frame = FloatArray(channels)
                    if (isFloat) {
                        val fb = buffer.asFloatBuffer()
                        frames = fb.remaining() / channels
                        if (count + frames > out.size) out = out.copyOf(maxOf(out.size * 2, count + frames))
                        for (i in 0 until frames) {
                            var sum = 0f
                            fb.get(frame)
                            for (c in 0 until channels) sum += frame[c]
                            out[count++] = sum / channels
                        }
                    } else {
                        val sb = buffer.asShortBuffer()
                        frames = sb.remaining() / channels
                        if (count + frames > out.size) out = out.copyOf(maxOf(out.size * 2, count + frames))
                        for (i in 0 until frames) {
                            var sum = 0f
                            for (c in 0 until channels) sum += sb.get() / 32768f
                            out[count++] = sum / channels
                        }
                    }
                }
                decoder.releaseOutputBuffer(outIndex, false)
                val enough = count >= (maxSeconds * sampleRate).toInt()
                if (enough || (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
        if (count == 0) return null
        return DecodedAudio(out.copyOf(count), sampleRate, if (firstTimeUs == Long.MIN_VALUE) 0 else firstTimeUs)
    } catch (e: Exception) {
        return null
    } finally {
        try { codec?.stop() } catch (_: Exception) {}
        codec?.release()
        extractor.release()
    }
}
