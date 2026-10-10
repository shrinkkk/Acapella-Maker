# Build plan

## Architecture

```
UI (Compose)
 ├─ Project screen: grid preview of takes, add/re-record/delete take
 ├─ Record screen: camera preview, metronome + backing toggles, record button
 └─ Settings: latency calibration, audio device, metronome defaults

Engine (Kotlin)
 ├─ ProjectRepository: takes, offsets, BPM, saved as JSON + media files in app storage
 └─ SessionController: coordinates camera + audio start/stop

Audio engine (C++ / Oboe, via JNI)
 ├─ One full-duplex stream: plays (metronome + backing mix), records mic
 ├─ Shared frame counter = master clock
 └─ Records the frame index at which recording started -> exact alignment

Export
 └─ Decode takes -> apply per-take offset -> compose grid -> mix audio -> MP4
```

## Sync model

- `outputLatency + inputLatency = roundTrip`, measured per output device
  (loopback: play clicks, hold earbud to mic, detect the click onset).
- A take's audio is placed at `startFrame - roundTrip` on the shared timeline.
- Video is matched to its own take's audio using the presentation timestamps from the recorder.

## Milestones (each one gets its own commit)

1. Project skeleton (Android Studio template), README, plan
2. Permissions + camera preview (CameraX)
3. Record a single video take and play it back
4. Oboe audio engine: metronome click on the shared clock
5. Record audio through Oboe in sync with the metronome
6. Multiple takes + grid layout preview
7. Backing-track playback of earlier takes while recording
8. Latency calibration screen + offset compensation
9. Per-take controls (volume, mute, nudge, re-record)
10. Export grid video to MP4
