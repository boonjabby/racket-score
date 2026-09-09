# Racket Score for Android and Wear OS

Native Android project containing a standalone Wear OS scorer and an early paired-phone live scoreboard.

## Features

### Watch

- Pickleball doubles and singles, tennis/padel, badminton, table tennis, squash, and racquetball.
- Official pickleball service sequence and scorer-perspective court positions.
- Spoken score, vibration, undo, restart, persistent match state, and winner celebration.
- Fully usable without a phone or internet connection.
- Winner screen includes an undo action for accidental match-point taps.
- Publishes a versioned, sequenced match snapshot through the secure Wear OS Data Layer when a paired Android phone is available.

### Android phone companion

- Receives live watch scoring updates.
- Recovers the latest watch state when opened after a match has started.
- Full-screen Court Display Mode with screen wake lock.
- Stores up to 50 completed watch matches locally.
- Retracts a completed result if the winning rally is undone.
- Flips Watch Live and Court View into the opponent's true court perspective, including service position.
- Editable labels replace the default My Side and Opponent names.
- Offline Round Robin organiser for four or more players, configurable courts, winner entry, fair rest rotation, random rounds, and split-and-rotate rounds.
- Round Robin supports up to eight independently advancing courts, waiting/paused players, and amendable matchups.
- Read-only live Round Robin sharing by temporary event code or QR code.
- Persistent high-contrast and coloured court themes.

## Project structure

- `engine/` — platform-independent scoring rules, live-match payload, and unit tests.
- `app/` — standalone Wear OS Compose app.
- `mobile/` — Android phone companion and Data Layer listener.

The watch and phone use the same application ID and signing certificate, as required by the Wear OS Data Layer. Their version codes remain unique across form factors.

## Build

1. Open this `wearos` directory in Android Studio.
2. Allow Android Studio to install Android SDK 36 if prompted.
3. Run `app` on a Wear OS watch or emulator.
4. Run `mobile` on the Android phone paired with that watch.

Command-line verification:

```shell
./gradlew test :app:assembleDebug :mobile:assembleDebug
```

## Verification status

- Eleven engine and live-payload tests pass.
- Watch and phone debug APKs build successfully with Android SDK 36.
- Watch scoring was field-tested on a Samsung Galaxy Watch Ultra before live synchronization was introduced.
- Physical watch-to-phone Data Layer verification is the next milestone.
