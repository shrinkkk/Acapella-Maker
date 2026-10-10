# Acapella Maker

A personal Android app for recording multi-part acapella videos on a phone.
Record video takes one at a time, hear earlier takes (and an optional metronome)
while recording the next, and get every part laid out in a grid, sample-accurately in sync.

## Core idea

Audio is the master clock. Every take, the metronome and the backing mix sit on one
shared sample timeline. Each new take is shifted by the measured round-trip latency
of the current output device (wired / Bluetooth earphones), so parts line up instead of
drifting late. Video follows its own take's audio.

## Planned features

- Record video takes one by one (front camera)
- Auto grid layout: 1 = full, 2 = stacked, 3 = stacked, 4 = 2x2, and so on
- Metronome (BPM, time signature, count-in), locked to the recording timeline
- Play earlier takes as a backing track while recording a new one
- Per-headset latency calibration (loopback click test)
- Per-take volume, mute, re-record, nudge offset
- Export the final grid video as MP4

## Tech stack

- Kotlin + Jetpack Compose (UI)
- Oboe (C++ via JNI) for low-latency, sample-accurate audio playback and capture
- CameraX for video capture
- Media3 Transformer / MediaCodec for compositing and export

## Status

Early development. See [docs/PLAN.md](docs/PLAN.md) for the build plan and milestones.

## Building

Open the project in Android Studio, connect an Android phone with USB debugging enabled, and press Run.
Personal project, not published on the Play Store.
