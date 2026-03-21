# obd-scanner-android

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-24-orange)
![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)

Open-source **Android OBD2 scanner** sample app showing a production-style integration of
[`kotlin-obd-api`](https://github.com/eltonvs/kotlin-obd-api) with modern Android architecture.

This repository is intended to be a canonical reference for developers who need to implement:

- **Bluetooth ELM327 discovery, pairing, and connection** on Android (SPP / Bluetooth Classic)
- **Live OBD-II PID polling** (speed, RPM, coolant, throttle, etc.)
- **Vehicle diagnostics** (VIN + DTC parsing + clear DTC)
- **Jetpack Compose + Hilt + DataStore + Flow** architecture
- **Quality checks and observability** (Lint, ktlint, detekt, unit tests, debug logs, telemetry)

---

## Table of Contents

- [Overview](#overview)
- [Quick Start Guide](#quick-start-guide)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [Bluetooth + OBD Integration Deep Dive](#bluetooth--obd-integration-deep-dive)
- [OBD Commands Used](#obd-commands-used)
- [Permissions & Security Model](#permissions--security-model)
- [State Management](#state-management)
- [Settings & Persistence](#settings--persistence)
- [Setup Guide](#setup-guide)
- [Build & Run](#build--run)
- [Quality Gates](#quality-gates)
- [Testing](#testing)
- [Compatibility Notes](#compatibility-notes)
- [Troubleshooting](#troubleshooting)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [SEO / Discoverability Tips (for maintainers)](#seo--discoverability-tips-for-maintainers)

---

## Overview

Many OBD Android repos are either:

- outdated examples,
- tightly coupled codebases,
- or missing architecture/testing guidance.

`obd-scanner-android` focuses on being:

- **current** (Kotlin + Compose + modern Android tooling),
- **educational** (clear architecture boundaries),
- **practical** (real Bluetooth + OBD command flow),
- **extensible** (easy to add more commands/transports).

If you're building an app around **ELM327**, **vehicle telemetry**, or **Android diagnostics tools**, this repo is designed to be your starting point.

At a high level, the app is organized around four main user flows:

- **Connection**: discover nearby Bluetooth Classic devices, pair adapters, and establish the RFCOMM/OBD session
- **Dashboard**: read live vehicle metrics with a polling scheduler tuned for fast/medium/slow sensor updates
- **Diagnostics**: read VIN + trouble codes and clear DTCs without competing with dashboard polling
- **Settings**: persist theme, polling interval, auto-connect, and telemetry preferences

Under the hood, the project keeps UI, domain, and data responsibilities separate while still staying practical for a real Android Bluetooth + OBD implementation.

This sample currently targets **Bluetooth Classic (SPP)** adapters. **BLE transport is not implemented** in this repository.

### Screenshots

| Connection | Dashboard |
|---|---|
| ![Android OBD2 scanner connection screen with paired Bluetooth ELM327 device](docs/images/connection-screen.png) | ![Android OBD2 scanner live dashboard showing speed RPM and sensor gauges](docs/images/dashboard-screen.png) |

| Diagnostics | Settings |
|---|---|
| ![Android OBD2 diagnostics screen with VIN and trouble code status](docs/images/diagnostics-screen.png) | ![Android OBD2 app settings screen with theme refresh interval and auto-connect](docs/images/settings-screen.png) |

---

## Quick Start Guide

If you're new to Android OBD2 apps, start here first. This section is intentionally practical and step-by-step.

### 1) What you need

- An Android phone/tablet with Bluetooth
- An ELM327-compatible **Bluetooth Classic (SPP)** OBD-II adapter
- A vehicle with an OBD-II port
- Android Studio + JDK 17 (if you want to build from source)

### 2) Build and run

```bash
./gradlew assembleDebug
```

Install and open the app from Android Studio (or install APK via adb).

### 3) Discover or pair your adapter (important)

Before using the app:

1. Open the app's **Connection** screen
2. Grant the requested Bluetooth permissions
3. Use **Scan Nearby** to discover nearby adapters
4. If your adapter is not already bonded, tap **Pair** from the nearby devices list and confirm the Android Bluetooth dialog
5. Return to the app and select the paired adapter

The app **discovers nearby Bluetooth Classic devices**, initiates **system pairing** for nearby devices, and connects to **paired devices** for the RFCOMM/OBD session.

### 4) First-run walkthrough

1. Open **Connection** screen
2. Grant requested Bluetooth permissions
3. Optionally tap **Scan Nearby** to confirm your adapter is visible
4. Select your paired OBD adapter
5. Tap **Connect**
6. Once connected, open:
   - **Dashboard** for live metrics (speed/RPM/etc.)
   - **Diagnostics** for VIN and DTCs

### 5) Common beginner issues

- **“Device not listed”**
  - usually means adapter is not paired at OS level
- **“Connected but no values”**
  - some ECUs do not expose all PIDs
  - try another vehicle or increase polling interval
- **“Permission denied / security exception”**
  - re-open app settings and allow Bluetooth permissions

### 6) Quick glossary

- **OBD-II**: standard vehicle diagnostics interface
- **ELM327**: common adapter chipset used by Bluetooth OBD dongles
- **PID**: parameter ID used to request sensor data (e.g., speed)
- **DTC**: Diagnostic Trouble Code (fault code)
- **VIN**: Vehicle Identification Number
- **MIL**: Malfunction Indicator Lamp (check-engine status)

### 7) More project details

The remaining sections in this README cover the implementation in depth, including:

- architecture layers (UI/domain/data)
- repository + transport internals
- kotlin-obd-api command integration
- quality tooling and testing strategy

---

## Features

### Connection
- Runtime permission request flow for Bluetooth discovery and connection
- List paired devices
- Scan nearby Bluetooth Classic devices from inside the app
- Start Bluetooth pairing for nearby devices from inside the app (system pairing dialog)
- Show pairing progress and refresh paired devices when bonding completes
- Connect/disconnect to ELM327-compatible adapter
- Connection state (`Disconnected`, `Connecting`, `Connected`, `Error`)
- Discovery state (`Idle`, `Starting`, `Discovering`, `Finished`, `Error`)
- Pairing state (`Idle`, `Pairing`, `Paired`, `Error`)

### Live Dashboard
- Tiered polling loop with configurable base interval
- Live gauges and sensor cards
- In-app debug log viewer (command/response/error timeline)
- Export current debug logs to a `.txt` file via the system document picker
- Optional structured telemetry capture for command timing, cycle summaries, and metric emissions

### Diagnostics
- VIN retrieval
- Trouble code retrieval (Mode 03)
- Trouble code clearing (Mode 04)
- Robust DTC parser:
  - direct standard-code extraction (`[PCBU][0-3][0-9A-F]{3}`)
  - hex fallback decoding to canonical 5-char codes
  - filters noise (`NO DATA`, `P0000`, duplicates)

### Settings
- Polling interval configuration
- System/light/dark theme selection
- Auto-connect to previous device
- Telemetry logging toggle

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt + KSP
- **Concurrency:** Coroutines + Flow
- **Persistence:** DataStore Preferences
- **OBD Library:** `com.github.eltonvs:kotlin-obd-api:v1.4.1`
- **Static Analysis:** Android Lint + ktlint + detekt
- **Testing:** JUnit4 + MockK + Turbine + coroutines-test

Build configuration:

- Compile SDK: `36`
- Min SDK: `24`
- Java target: `17`

---

## Project Structure

```text
app/src/main/kotlin/studio/etsoftware/obdapp/
├── data/
│   ├── connection/
│   │   ├── BluetoothDiscoveryManager.kt
│   │   ├── BluetoothTransport.kt
│   │   └── ObdTransport.kt
│   ├── di/
│   │   ├── RepositoryModule.kt
│   │   ├── SettingsModule.kt
│   │   ├── TelemetryModule.kt
│   │   └── TransportModule.kt
│   ├── repository/
│   │   ├── DashboardPollingScheduler.kt
│   │   └── ObdRepositoryImpl.kt
│   ├── settings/
│   │   └── PollingSettingsRepositoryImpl.kt
│   └── telemetry/
│       ├── TelemetryRecorder.kt
│       ├── TelemetryRepositoryImpl.kt
│       └── TelemetrySettingsDataSource.kt
├── domain/
│   ├── model/
│   │   ├── DashboardMetricsSnapshot.kt
│   │   ├── DeviceInfo.kt
│   │   ├── DiagnosticInfo.kt
│   │   ├── DiscoveryState.kt
│   │   ├── PairingState.kt
│   │   ├── TelemetryEvent.kt
│   │   └── VehicleMetric.kt
│   ├── repository/
│   │   ├── ObdRepository.kt
│   │   ├── PollingSettingsRepository.kt
│   │   └── TelemetryRepository.kt
│   └── usecase/
├── ui/
│   ├── components/
│   ├── feature/
│   │   ├── connection/
│   │   ├── dashboard/
│   │   ├── diagnostics/
│   │   └── settings/
│   ├── navigation/
│   │   ├── NavGraph.kt
│   │   └── Screen.kt
│   ├── theme/
│   └── MainActivity.kt
├── util/
│   ├── LogExporter.kt
│   ├── LogExportFormatter.kt
│   ├── LogManager.kt
│   └── PreferencesManager.kt
└── ObdScannerApp.kt
```

---

## Architecture

Layered architecture with clean separation:

- **UI layer**: composables + ViewModels (presentation state)
- **Domain layer**: use cases + repository contracts + domain models
- **Data layer**: repository implementations + transport implementation + settings/telemetry integration + external library integration

### Data flow

Core OBD flow:

```text
Compose Screen
   -> ViewModel
      -> UseCase
         -> ObdRepository (interface)
            -> ObdRepositoryImpl
               -> ObdTransport (BluetoothTransport)
               -> kotlin-obd-api commands
```

Supporting UI utilities such as debug log export and telemetry inspection follow a lighter path:

```text
DashboardScreen
   -> DashboardViewModel
      -> ObserveTelemetryEventsUseCase / LogManager / LogExportFormatter / LogExporter
```

### Why this architecture works

- UI remains free from transport protocol details
- business actions are explicit and testable
- polling/diagnostics coordination stays inside the repository layer
- swapping transport implementation is straightforward

---

## Bluetooth + OBD Integration Deep Dive

## 1) Device discovery sequence

1. User taps **Scan Nearby** in `ConnectionScreen`
2. `ConnectionViewModel.startDiscovery()` delegates to `ObdRepository.startDiscovery()`
3. `BluetoothDiscoveryManager`:
   - validates Bluetooth scan/connect permissions
   - registers a short-lived broadcast receiver for discovery events
   - starts Classic Bluetooth discovery via `BluetoothAdapter.startDiscovery()`
   - emits `DiscoveryState` updates as devices are found
4. The ViewModel filters out already-paired devices and renders nearby unpaired devices separately
5. Nearby unpaired devices can start Android's system Bluetooth pairing flow directly from the app, with bond-state progress reflected in `PairingState`

## 2) Device connection sequence

1. User selects a paired adapter in `ConnectionScreen`
2. `ConnectionViewModel.connect()` invokes `ConnectDeviceUseCase`
3. `ObdRepositoryImpl.connect()`:
   - stops any active Bluetooth discovery first
   - validates `BLUETOOTH_CONNECT` permission
   - delegates socket creation/connection to `BluetoothTransport`
   - `BluetoothTransport` defensively cancels discovery before RFCOMM connect
   - creates `ObdDeviceConnection(inputStream, outputStream)`
   - performs adapter bootstrap:
     - `ATZ` (`ResetAdapterCommand`)
     - `ATE0` (`SetEchoCommand(Switcher.OFF)`)

## 3) Polling lifecycle

- Dashboard starts polling only when connected
- Polling executes on IO coroutine scope
- `DashboardPollingScheduler` staggers metrics by fast/medium/slow tiers instead of sending every command every cycle
- Polling interval changes can be applied while the polling loop is active
- Disconnect cancels polling and closes transport safely

## 4) Diagnostics lifecycle

- `DiagnosticsViewModel` owns diagnostics screen state
- Reads VIN and trouble codes on demand / when connected
- Can clear trouble codes through Mode 04
- Repository pauses dashboard polling during diagnostics operations and resumes afterward when appropriate
- Applies resilient DTC normalization before UI rendering

---

## OBD Commands Used

| Purpose | Command Class | Typical PID/Mode |
|---|---|---|
| Reset adapter | `ResetAdapterCommand` | `ATZ` |
| Echo off | `SetEchoCommand(Switcher.OFF)` | `ATE0` |
| Vehicle speed | `SpeedCommand` | `010D` |
| Engine RPM | `RPMCommand` | `010C` |
| Coolant temp | `EngineCoolantTemperatureCommand` | `0105` |
| Intake air temp | `AirIntakeTemperatureCommand` | `010F` |
| MAF | `MassAirFlowCommand` | `0110` |
| Throttle position | `ThrottlePositionCommand` | `0111` |
| Fuel level | `FuelLevelCommand` | `012F` |
| VIN | `VINCommand` | Mode 09 |
| Trouble codes | `TroubleCodesCommand` | Mode 03 |
| Clear trouble codes | `ResetTroubleCodesCommand` | Mode 04 |

---

## Permissions & Security Model

Manifest permissions:

- `BLUETOOTH` / `BLUETOOTH_ADMIN` (for API <= 30)
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN` (`neverForLocation`)
- `ACCESS_FINE_LOCATION` (API <= 30 only, for discovery)

Runtime permission request is handled in `ConnectionScreen`.

Runtime behavior:

- API 31+: request `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`
- API <= 30: request location permission when starting Bluetooth discovery
- API <= 30: Bluetooth discovery also requires system Location services to be turned on

Defensive checks in data layer:

- repository/discovery/transport guard sensitive calls with permission checks
- sensitive Bluetooth calls handle `SecurityException` gracefully
- discovery receiver is registered only for active scan sessions and unregistered when scanning finishes/stops
- pairing and connection flows stop discovery before attempting RFCOMM work

---

## State Management

- Repository exposes `StateFlow<ConnectionState>` for connection status
- Repository also exposes `StateFlow<DiscoveryState>` for Bluetooth discovery progress/results
- Repository also exposes `StateFlow<PairingState>` for Bluetooth pairing progress/results
- Repository exposes `StateFlow<DashboardMetricsSnapshot>` for dashboard-friendly metric snapshots
- Low-level metric events are also available as a `Flow<VehicleMetric>` stream
- Telemetry events are exposed separately via the telemetry repository/use cases when enabled
- Screens consume state with `collectAsStateWithLifecycle()`

This keeps UI reactive and lifecycle-safe.

---

## Settings & Persistence

`PreferencesManager` (DataStore) stores:

- polling interval
- theme (`system` default)
- last connected device metadata
- auto-connect flags
- telemetry enabled state
- previous-connection state used by the current auto-connect flow

Auto-connect flow:

- dashboard startup checks persisted state before attempting reconnect
- if enabled and a valid previous device exists, reconnect attempt is triggered

---

## Setup Guide

### Hardware prerequisites

- Android phone/tablet (Bluetooth capable)
- ELM327-compatible OBD-II adapter (Bluetooth Classic/SPP)
- Vehicle with OBD-II port

### Pairing

1. Open app and grant requested permissions
2. Use **Scan Nearby** to confirm the adapter is visible
3. Tap **Pair** for a nearby device and confirm the Android Bluetooth pairing dialog
4. The app refreshes the paired list when bonding completes; select the paired adapter and connect

---

## Build & Run

```bash
./gradlew assembleDebug
```

Then install via Android Studio or `adb install`.

---

## Quality Gates

Run full quality pipeline:

```bash
./gradlew check
```

Run individually:

```bash
./gradlew app:testDebugUnitTest
./gradlew app:lint
./gradlew app:ktlintCheck
./gradlew :app:detekt
```

> `detekt` is wired as a custom Gradle task using `detekt-cli` for compatibility with the current AGP/Kotlin setup.

---

## Testing

Current test coverage includes:

- use case behavior
- ViewModel state transitions and error handling
- dashboard polling scheduler behavior
- debug log export formatting

Frameworks:

- `junit:junit`
- `io.mockk:mockk`
- `kotlinx-coroutines-test`
- `app.cash.turbine`

---

## Compatibility Notes

- This sample targets **Bluetooth Classic (SPP)** transport
- BLE transport is not implemented in this repository
- Not all ECUs expose all PIDs; missing sensor values may be expected on some vehicles

Toolchain note:

- AGP/KSP + built-in Kotlin currently requires compatibility handling in this project (`android.disallowKotlinSourceSets=false`)

---

## Troubleshooting

### Adapter not listed

- ensure Bluetooth is enabled on the phone/tablet
- on Android 11 and below, ensure system Location is turned on before tapping **Scan Nearby**
- on Android 11 and below, ensure Location permission is granted before scanning
- tap **Scan Nearby** and wait for discovery to finish
- if the adapter appears under nearby devices, tap **Pair** and complete the Android Bluetooth pairing dialog
- verify Bluetooth permissions are granted

### Connection fails quickly

- ensure adapter has power (ignition state)
- ensure the adapter is paired before connecting
- ensure no other app is currently connected to adapter
- unplug/replug adapter and retry

### No live values

- ECU may not support specific PIDs
- increase polling interval and retry
- test with another compatible vehicle

### Build issues

- ensure JDK 17
- run `./gradlew clean check`
- sync Gradle in Android Studio after dependency updates

---

## Roadmap

- [ ] BLE transport implementation
- [ ] richer DTC descriptions (lookup catalog)
- [ ] historical charts
- [ ] integration tests with fake transport
- [ ] sample video/GIF walkthrough in README

---

## Contributing

Contributions are welcome.

Please:

1. keep architecture boundaries clean (UI/domain/data)
2. run `./gradlew check` before PR
3. include tests for behavior changes
4. prefer focused, small PRs

---

## SEO / Discoverability Tips (for maintainers)

For better GitHub and Google discoverability, set the repo metadata:

### Suggested GitHub repository description

> Android OBD2 scanner sample app (Kotlin + Jetpack Compose) using kotlin-obd-api with Bluetooth ELM327 connection, live PID polling, and diagnostics.

### Suggested GitHub topics

`android` · `kotlin` · `jetpack-compose` · `obd2` · `obd-ii` · `elm327` · `bluetooth` · `vehicle-diagnostics` · `automotive` · `telematics`

### Suggested social preview image text

> "OBD Scanner Android — Kotlin + Compose + kotlin-obd-api"

### Suggested docs additions

- publish screenshots/GIFs under `docs/images`
- add `LICENSE` file
- add `CHANGELOG.md`
- add releases with APK/demo video links

---

## Repository Identity

- **Repository name:** `obd-scanner-android`
- **Goal:** canonical open-source Android sample for implementing `kotlin-obd-api`.
