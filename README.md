# Rider Intercom

**Rider-to-rider voice for motorcyclists.** No cloud, no subscription, no internet — just two phones and Bluetooth.

<p align="center">
  <img src="screenshots/02_home_ready.png" width="270" alt="Home screen" />
  &nbsp;&nbsp;
  <img src="screenshots/04_hosting.png" width="270" alt="Active call" />
  &nbsp;&nbsp;
  <img src="screenshots/03_settings.png" width="270" alt="Settings" />
</p>

## How to use

1. **Install** the app on two Android phones (Android 10+).
2. **Pair** the phones in Bluetooth settings (standard Bluetooth pairing).
3. **Grant permissions** — microphone, Bluetooth, and notifications when prompted.
4. On one phone, tap **Host Ride** → **Start hosting**. It will wait for a buddy.
5. On the other phone, tap **Join Rider** → select the host phone from paired devices.
6. **Talk.** The call is voice-activated by default — just speak and the channel opens. Your buddy hears you through their phone speaker or helmet headset.

### Talk modes

| Mode | How it works |
|------|-------------|
| **Open call** (default) | Voice-activated. Speak and the detector opens the channel automatically. First speaker holds the floor; others are muted until they stop. |
| **Push to talk** | The detector is off. Hold the on-screen button, the notification button, or a Bluetooth headset button to talk. |

### Microphone options

- **Phone mic** — simplest setup. Works well at town speeds; wind noise becomes a problem on the highway.
- **Wired boom mic** (USB-C or 3.5 mm) — best wind rejection. Headset stays on A2DP so music remains stereo.
- **LE Audio headset** — Android 13+ only. Mic and high-quality output simultaneously. Few helmet headsets support this yet.
- **Headset mic** — convenient but Android suspends A2DP while the headset mic is open, so music turns mono for the whole call.

## What works

- Voice calls over Bluetooth RFCOMM between two phones
- Voice activity detection and half-duplex floor arbitration
- IMA ADPCM codec at 16 kHz / 20 ms frames
- Automatic transport switching between Bluetooth and Wi-Fi Direct
- Foreground service that keeps the call alive with the screen off
- Headset button control (play/pause to cut in)
- RTT measurement and clock synchronization between phones
- BLE nearby-host discovery (find hosting phones without manual pairing)
- HFP Audio Gateway for bridging with helmet Bluetooth intercoms
- Settings for talk mode, mic source, noise suppression level, and transport preference

## Known issues

- **Noise suppression stuck on Strong**: On some devices, the noise cancellation level selector in Settings does not apply correctly — the denoiser stays on the Strong preset regardless of what is selected. The spectral denoiser itself works (it does reduce wind/engine noise), but the three levels (Light / Medium / Strong) may not switch. This is a UI-to-engine binding issue, not a signal processing bug.

- **Wi-Fi Direct joiner path**: The Wi-Fi Direct transport (for longer range) has not been road-tested on the joiner side. Hosting works, but the client connecting over Wi-Fi Direct may not complete the group formation on all devices.

## Architecture

```
Phone A                                    Phone B
┌─────────────┐     RFCOMM / Wi-Fi Direct     ┌─────────────┐
│  Mic → ADPCM ├──────────────────────────────►│ ADPCM → Spk │
│  Spk ← ADPCM │◄──────────────────────────────┤ Mic → ADPCM │
└─────────────┘                                └─────────────┘
```

**Wire protocol**: `SYNC(0xAA) | type(1) | seq(2) | flags(1) | len(2) | payload`

- 7-byte header, max 512-byte payload
- HELLO handshake with version, sample rate, codec, session ID
- PING/PONG for RTT measurement and clock sync
- FLOOR_TAKEN / FLOOR_RELEASE for half-duplex arbitration
- SWITCH for coordinated transport handover

**Audio**: 16 kHz, 20 ms frames (320 samples), IMA ADPCM at 32.6 kbps. Each frame is 163 bytes (3-byte state header + 160 bytes of 4-bit samples).

## Related

This intercom module has been merged into the [KTM Nav Gen3](https://github.com/Pavanayi1/KTM-Nav-GEN3) app, which adds turn-by-turn navigation on the KTM Gen 3 motorcycle dashboard. See that repo for the combined bike control + intercom experience.

## Building

```bash
git clone https://github.com/Pavanayi1/intercom.git
cd intercom

./gradlew :app:assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Requirements**: Android Studio with JDK 17, Android SDK 35, min SDK 29 (Android 10).

## Project structure

```
app/src/main/java/com/intercom/app/
├── audio/          # ADPCM codec, mic capture, jitter buffer, denoiser, voice player
├── call/           # CallController, IntercomService (foreground service)
├── discovery/      # Host beacon & scanner (BLE advertising for nearby discovery)
├── floor/          # Voice activity detector, half-duplex floor arbiter
├── hfp/            # HFP Audio Gateway server (for helmet intercom bridges)
├── media/          # Media button handling, audio focus management
├── protocol/       # Wire protocol codec & message types
├── transport/      # RFCOMM link, Wi-Fi Direct link, LinkSelector, SwitchPolicy
├── ui/             # Compose UI — start screen, call screen, settings
└── util/           # Permissions, preferences, OEM detection
tools/
├── mac_intercom.py    # Mac test client — connects via TCP, captures mic audio
├── mac_hfp_ag.swift   # Mac HFP Audio Gateway for testing with BT intercoms
└── hfp_hf_emulator.py # HFP Hands-Free emulator for testing HfpAgServer
```

## Testing with a Mac

For development testing, the app accepts TCP connections alongside Bluetooth. Connect your phone via USB and forward the port:

```bash
adb forward tcp:9333 tcp:9333
```

Start hosting in the app, then run the Mac client:

```bash
pip install sounddevice numpy
python3 tools/mac_intercom.py
```

## License

Private — not for redistribution.
