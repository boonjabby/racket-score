# Racket Score for Wear OS

Native, standalone Samsung Galaxy Watch / Wear OS prototype for pickleball doubles.

## Prototype features

- Tap the upper or lower half to record the rally winner.
- Official `0–0–2` opening service sequence.
- Server 1 and Server 2 remain on opposite physical courts.
- Opponent positions are mirrored from the scorer's perspective.
- Spoken score is always ordered from the server's perspective.
- Short vibration after each recorded rally.
- Undo and new-game controls sit directly on the center net.
- Configurable opening side and server number.
- Match state and the latest 30 undo steps survive app restarts.
- Trophy and monochrome confetti for the winner.

## Supported watches

The prototype uses `minSdk 30`, covering Samsung Galaxy Watch 4 and newer Wear OS models. It is declared as a standalone watch app, so scoring does not require the phone app.

## Open and run

1. Install the latest stable Android Studio.
2. Open this `wearos` directory as a project.
3. Allow Android Studio to install Android SDK 36 if prompted.
4. Create a Wear OS emulator or enable developer options and wireless debugging on a Galaxy Watch.
5. Select the `app` run configuration and run it on the watch.

## Project structure

- `engine/` — platform-independent pickleball scoring rules and unit tests.
- `app/` — Wear OS Compose interface, speech, vibration and local persistence.

Phone/watch synchronization is intentionally deferred. The watch works independently first; a Data Layer bridge can be added once real-watch interaction is proven.

## Verification status

- Five native scoring-engine tests pass.
- The debug APK builds successfully with Android SDK 36.
- Installed and interaction-tested on a 384×384 round Wear OS 5 emulator.
- Verified a rally updates the score and moves the serving position to the opposite court.

The next verification step is installation on a physical Samsung Galaxy Watch.
