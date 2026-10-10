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

## Recording UX (agreed direction)

- **New project:** choose the number of takes up front. That fixes the grid.
- **Grid layouts:** 1 = full screen, 2 = 2 stacked, 3 = 3 stacked, 4 = 2x2, larger counts TBD.
- **The record screen is the grid itself**, laid out exactly as the final video will be:
  - Tap a cell to select it (highlighted). Only the selected cell shows the live camera preview.
  - Record records into the selected cell.
  - Empty cells are black. Recorded cells play their take.
  - Each cell has a mute toggle that decides whether you hear that take while recording.
- **Crop rule:** each cell shows a centre crop of the camera frame, and export uses the same crop maths,
  so what you see in a cell is what ends up in the final video.

Open questions (to decide later):
- Final video aspect ratio (9:16 portrait vs other)
- Layouts for 5+ takes
- Per-cell controls beyond mute (volume, nudge, re-record confirm)

## Milestones (each one gets its own commit)

1. Project skeleton (Android Studio template), README, plan - done
2. Permissions + camera preview (CameraX) - done
3. Record a single video take and play it back - done
4. Project setup (take count) + grid record screen: select cell, preview in cell, record into cell,
   recorded cells show their take
5. Oboe audio engine: metronome click on the shared clock
6. Record audio through Oboe in sync with the metronome
7. Recorded cells play back in sync while recording a new take (backing track), with per-cell mute
8. Latency calibration screen + offset compensation
9. Per-take controls (volume, nudge, re-record)
10. Export grid video to MP4 (same crop as on screen)
