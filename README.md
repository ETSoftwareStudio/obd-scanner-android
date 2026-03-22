# OBD Scanner Android

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-24-orange)
![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)

![OBD Scanner Android hero](docs/images/obd-scanner-android-hero.png)

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
- [Contributing](#contributing)

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
│   ├── diagnostics/
│   │   ├── DiagnosticsService.kt
│   │   └── DtcParser.kt
│   ├── di/
│   │   ├── LoggingModule.kt
│   │   ├── RepositoryModule.kt
│   │   ├── SettingsModule.kt
│   │   ├── TelemetryModule.kt
│   │   └── TransportModule.kt
│   ├── logging/
│   │   ├── DebugLogRepositoryImpl.kt
│   │   ├── LogExporter.kt
│   │   ├── LogExportFormatter.kt
│   │   ├── LogExportRepositoryImpl.kt
│   │   └── LogManager.kt
│   ├── polling/
│   │   ├── DashboardMetricsStore.kt
│   │   ├── DashboardPollingCoordinator.kt
│   │   └── DashboardPollingScheduler.kt
│   ├── repository/
│   │   ├── ConnectionRepositoryImpl.kt
│   │   ├── DashboardRepositoryImpl.kt
│   │   ├── DiagnosticsRepositoryImpl.kt
│   │   └── DiscoveryRepositoryImpl.kt
│   ├── session/
│   │   ├── ObdCommandExecutor.kt
│   │   └── ObdSessionManager.kt
│   ├── settings/
│   │   ├── AppSettingsRepositoryImpl.kt
│   │   ├── PollingSettingsRepositoryImpl.kt
│   │   └── PreferencesManager.kt
│   └── telemetry/
│       ├── TelemetryRecorder.kt
│       ├── TelemetryRepositoryImpl.kt
│       └── TelemetrySettingsDataSource.kt
├── domain/
│   ├── model/
│   │   ├── DashboardMetricsSnapshot.kt
│   │   ├── DebugLogEntry.kt
│   │   ├── DeviceInfo.kt
│   │   ├── DiagnosticInfo.kt
│   │   ├── DiscoveryState.kt
│   │   ├── PairingState.kt
│   │   ├── TelemetryEvent.kt
│   │   └── VehicleMetric.kt
│   ├── repository/
│   │   ├── AppSettingsRepository.kt
│   │   ├── ConnectionRepository.kt
│   │   ├── DashboardRepository.kt
│   │   ├── DebugLogRepository.kt
│   │   ├── DiagnosticsRepository.kt
│   │   ├── DiscoveryRepository.kt
│   │   ├── LogExportRepository.kt
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
└── ObdScannerApp.kt
```

---

## Architecture

Layered architecture with stricter dependency direction:

- **UI layer**: composables + ViewModels that depend on **use cases and domain models only**
- **Domain layer**: focused repository contracts + use cases + domain models
- **Data layer**: Android transport/platform integrations + repository implementations + orchestrators/services + observability/persistence details

### Data flow

Core OBD flow now goes through focused repository contracts instead of a single broad gateway:

```text
Compose Screen
   -> ViewModel
      -> UseCase
         -> ConnectionRepository / DiscoveryRepository / DashboardRepository / DiagnosticsRepository
            -> repository impls
               -> session / polling / diagnostics services
                  -> kotlin-obd-api commands + Bluetooth transport
```

The data layer itself is decomposed into role-based collaborators:

```text
ConnectionRepositoryImpl
   -> BluetoothDiscoveryManager
   -> ObdSessionManager
      -> ObdCommandExecutor
      -> ObdTransport (BluetoothTransport)

DashboardRepositoryImpl
   -> DashboardPollingCoordinator
      -> DashboardPollingScheduler
      -> DashboardMetricsStore

DiagnosticsRepositoryImpl
   -> DashboardPollingCoordinator.runWithPollingPaused(...)
   -> DiagnosticsService
      -> DtcParser
```

Debug log export and telemetry inspection are also routed through domain-facing contracts:

```text
DashboardScreen
   -> DashboardViewModel
      -> ObserveDebugLogsUseCase / BuildLogExportTextUseCase / ExportLogsUseCase / ObserveTelemetryEventsUseCase
```

### Why this architecture works

- UI remains free from transport, DataStore, and logging implementation details
- repository contracts are focused by responsibility, which improves testability and change isolation
- polling, diagnostics, session lifecycle, logging, and settings each have explicit owners in the data layer
- Android-specific APIs stay at the edges while business-facing flows stay in domain/use cases

---

## Bluetooth + OBD Integration Deep Dive

## 1) Device discovery sequence

1. User taps **Scan Nearby** in `ConnectionScreen`
2. `ConnectionViewModel.startDiscovery()` invokes `StartDiscoveryUseCase`
3. `DiscoveryRepositoryImpl` delegates to `BluetoothDiscoveryManager`, which:
   - validates Bluetooth scan/connect permissions
   - registers a short-lived broadcast receiver for discovery events
   - starts Classic Bluetooth discovery via `BluetoothAdapter.startDiscovery()`
   - emits `DiscoveryState` updates as devices are found
4. The ViewModel filters out already-paired devices and renders nearby unpaired devices separately
5. Nearby unpaired devices can start Android's system Bluetooth pairing flow directly from the app, with bond-state progress reflected in `PairingState`

## 2) Device connection sequence

1. User selects a paired adapter in `ConnectionScreen`
2. `ConnectionViewModel.connect()` invokes `ConnectDeviceUseCase`
3. `ConnectionRepositoryImpl.connect()` stops any active discovery and delegates session setup to `ObdSessionManager`
4. `ObdSessionManager.connect()`:
   - validates `BLUETOOTH_CONNECT` permission
   - delegates socket creation/connection to `BluetoothTransport`
   - `BluetoothTransport` defensively cancels discovery before RFCOMM connect
   - creates `ObdDeviceConnection(inputStream, outputStream)`
   - performs adapter bootstrap through `ObdCommandExecutor`:
     - `ATZ` (`ResetAdapterCommand`)
     - `ATE0` (`SetEchoCommand(Switcher.OFF)`)
   - cleans up partially-open sessions if bootstrap fails

## 3) Polling lifecycle

- Dashboard starts polling only when connected
- `DashboardRepositoryImpl` delegates polling lifecycle to `DashboardPollingCoordinator`
- `DashboardPollingCoordinator` executes on an IO coroutine scope
- `DashboardPollingScheduler` decides which metrics are due using fast/medium/slow tiers instead of sending every command every cycle
- Polling interval changes can be applied while the polling loop is active
- Transport loss and disconnect both stop polling safely and update connection state through the session layer

## 4) Diagnostics lifecycle

- `DiagnosticsViewModel` owns diagnostics screen state
- Reads VIN and trouble codes on demand / when connected
- Can clear trouble codes through Mode 04
- `DiagnosticsRepositoryImpl` runs diagnostics through `DashboardPollingCoordinator.runWithPollingPaused(...)` so diagnostics do not race with dashboard polling
- `DiagnosticsService` performs VIN / DTC command execution
- `DtcParser` handles resilient DTC normalization before UI rendering

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

- `ConnectionRepository` exposes `StateFlow<ConnectionState>` for connection status
- `DiscoveryRepository` exposes `StateFlow<DiscoveryState>` and `StateFlow<PairingState>` for Bluetooth discovery/pairing progress
- `DashboardRepository` exposes `StateFlow<DashboardMetricsSnapshot>` for dashboard-friendly metric snapshots
- Low-level metric events are still produced in the data layer via `DashboardMetricsStore`
- `TelemetryRepository` exposes telemetry events through dedicated use cases when enabled
- Screens consume state with `collectAsStateWithLifecycle()`

This keeps UI reactive and lifecycle-safe.

---

## Settings & Persistence

`PreferencesManager` (DataStore, kept in the data layer) stores:

- polling interval
- theme (`system` default)
- last connected device metadata
- auto-connect flags
- telemetry enabled state
- previous-connection state used by the current auto-connect flow

Those values are surfaced to the rest of the app through domain-facing contracts such as `AppSettingsRepository`, `PollingSettingsRepository`, and `TelemetryRepository`.

Auto-connect flow:

- dashboard startup checks persisted state through use cases before attempting reconnect
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

- focused use case behavior against domain contracts
- ViewModel state transitions and error handling
- dashboard polling scheduler behavior
- dashboard metrics publication/state snapshot behavior
- polling coordinator pause/access behavior
- diagnostics service behavior
- DTC parser normalization/hex decoding
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

## Contributing

Contributions are welcome.

- Open an issue to explain the problem you want to solve: [Open an issue](https://github.com/ETSoftwareStudio/obd-scanner-android/issues)
- For larger changes, discuss the approach first, then open a PR (or draft PR): [Current PRs](https://github.com/ETSoftwareStudio/obd-scanner-android/pulls)
- Run local verification before opening a PR: `./gradlew clean check`
- Add or update tests for feature work and behavior changes
