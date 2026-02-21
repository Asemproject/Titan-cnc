# Titan CNC - Ultimate Offline Android CNC Controller

A full-featured, offline-first Android CNC Controller app designed for workshop environments. Supports GRBL v1.1, grblHAL, and FluidNC firmware.

## Features

### 🔌 Connectivity & Streaming (100% Offline)
- **USB OTG Support**: CH340, CP2102, FTDI, PL2303 using `usb-serial-for-android`
- **Wireless**: Bluetooth Classic/BLE and Local Wi-Fi (Telnet/WebSocket for FluidNC/grblHAL)
- **Protocol**: Character Counting buffer management to prevent machine stuttering

### 📊 Dynamic Dashboard & Jogging
- **Real-time DRO**: Machine (MPos) & Work Position (WPos) with Zero/Home/Probe functions
- **Fluid Jogging**: Haptic-feedback D-Pad for X, Y, Z, and A-axis
- **Overrides**: Responsive sliders for Feed Rate (10-200%) and Spindle Speed (RPM)
- **Safety**: High-contrast Floating Emergency Stop (E-Stop) button

### 📝 Integrated Advanced Editors
- **G-Code Editor**: Syntax highlighting for G/M codes, Find & Replace, Line Numbering
- **YAML/Config Editor**: Custom UI to edit FluidNC 'config.yaml' or GRBL '$$' settings directly
- **Image-to-GCode Converter**:
  - Raster-to-GCode engine using OpenCV
  - Dithering (Floyd-Steinberg, Atkinson, Jarvis-Judice-Ninke)
  - Thresholding (Binary, Adaptive, Otsu)
  - Power Scaling (S-min/max) and Scan Direction (H/V/Diag)
  - Real-time path preview before sending

### 🔧 Tool & Bit Library (Local Database)
- **Tool Manager**: Room Database to store router bits (Endmill, V-bit, Ballnose)
- **Parameters**: Diameter, Flutes, Max Depth, Recommended Feed/Speed
- **Chipload Calculator**: Built-in calculator to suggest optimal Feed Rate based on bit and material

### 🎨 Visualizer & UI/UX
- **Tech Stack**: Kotlin + Jetpack Compose (Material 3)
- **3D Visualizer**: High-performance OpenGL ES engine for real-time toolpath tracking
- **Adaptive Layout**:
  - Phone: Bottom-tabbed navigation (Dashboard, Editor, Tool, Console)
  - Tablet: Multi-pane "Command Center" layout for larger screens
- **Visual Style**: "Industrial Cyber-Dark" theme (#121212 background with #00E5FF accents)

## Architecture

- **MVVM Pattern**: With Coroutines and StateFlow for reactive UI updates
- **Dependency Injection**: Hilt
- **Database**: Room for local tool storage
- **Image Processing**: OpenCV 4.9.0
- **Serial Communication**: usb-serial-for-android 3.7.0

## Project Structure

```
TitanCNC/
├── app/
│   ├── src/main/java/com/titancnc/
│   │   ├── data/
│   │   │   ├── database/     # Room database and DAOs
│   │   │   └── model/        # Data models (Tool, etc.)
│   │   ├── di/               # Hilt dependency injection modules
│   │   ├── service/          # Core services
│   │   │   ├── ConnectionManager.kt    # USB/BT/WiFi connectivity
│   │   │   └── GCodeSender.kt          # GRBL streaming with buffer management
│   │   ├── ui/
│   │   │   ├── screens/      # Compose screens
│   │   │   │   ├── DashboardScreen.kt
│   │   │   │   ├── GCodeEditorScreen.kt
│   │   │   │   ├── ToolLibraryScreen.kt
│   │   │   │   └── ConsoleScreen.kt
│   │   │   ├── theme/        # Material 3 theme
│   │   │   └── viewmodel/    # ViewModels
│   │   ├── utils/            # Utilities
│   │   │   ├── ChiploadCalculator.kt
│   │   │   └── ImageToGcodeConverter.kt
│   │   ├── visualizer/       # OpenGL ES visualizer
│   │   │   └── GCodeVisualizer.kt
│   │   ├── MainActivity.kt
│   │   └── TitanCNCApplication.kt
│   ├── src/main/cpp/         # Native code for OpenCV
│   ├── src/main/res/         # Android resources
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml    # Version catalog
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 🚀 Quick Start

### Download Pre-built APK

[![Build Status](https://github.com/YOUR_USERNAME/TitanCNC/workflows/Build%20Titan%20CNC/badge.svg)](https://github.com/YOUR_USERNAME/TitanCNC/actions)

Download the latest APK from [Releases](https://github.com/YOUR_USERNAME/TitanCNC/releases)

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK 35
- NDK (for OpenCV native code)

### Option 1: Build dengan GitHub Actions (Recommended)

Build otomatis setiap push ke repository:

1. **Fork repository ini**
2. **Push ke branch `main`**
3. **GitHub Actions akan otomatis build**
4. **Download APK dari tab Actions → Artifacts**

#### Setup Release dengan Signed APK

1. Buat keystore:
```bash
keytool -genkey -v -keystore titancnc-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias titancnc
base64 -w 0 titancnc-keystore.jks > keystore-base64.txt
```

2. Tambahkan secrets di GitHub (Settings → Secrets → Actions):
   - `KEYSTORE_BASE64`: Isi dari `keystore-base64.txt`
   - `KEYSTORE_PASSWORD`: Password keystore
   - `KEY_ALIAS`: `titancnc`
   - `KEY_PASSWORD`: Password key

3. Push tag untuk release:
```bash
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

### Option 2: Build Lokal dengan Android Studio

1. Clone repository:
```bash
git clone https://github.com/yourusername/titancnc.git
cd titancnc
```

2. Open di Android Studio

3. Sync Gradle:
```bash
./gradlew --refresh-dependencies
```

4. Build Debug APK:
```bash
./gradlew assembleDebug
```

5. Install ke device:
```bash
./gradlew installDebug
```

### Option 3: Build dengan Command Line

```bash
# Grant permission
chmod +x gradlew

# Build Debug
./gradlew assembleDebug

# Build Release
./gradlew assembleRelease

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### OpenCV Setup

The project uses OpenCV 4.9.0 via Gradle:

```kotlin
implementation("org.opencv:opencv:4.9.0")
```

Untuk native code support, pastikan NDK terinstall:
1. SDK Manager → SDK Tools
2. Centang "NDK (Side by side)"
3. Apply

## Key Components

### 1. ConnectionManager
Handles all connectivity options:
- USB Serial (CH340, CP2102, FTDI, PL2303)
- Bluetooth Classic/BLE
- WiFi (Telnet/WebSocket)

### 2. GCodeSender
Implements GRBL character counting protocol:
- 128-byte buffer management
- Real-time command support
- Status polling
- Override controls

### 3. ImageToGcodeConverter
OpenCV-based image processing:
- Dithering algorithms
- Thresholding methods
- Multiple engraving modes
- Preview generation

### 4. ChiploadCalculator
Machining parameter calculator:
- Feed rate calculation
- Spindle speed optimization
- Material-specific recommendations
- Safety validation

## Supported Firmware

- **GRBL v1.1**: Full support with all features
- **grblHAL**: Via WebSocket/Telnet
- **FluidNC**: Via WebSocket with config.yaml editing

## Permissions Required

- `INTERNET`: WiFi connectivity
- `BLUETOOTH` / `BLUETOOTH_ADMIN`: Bluetooth connectivity
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`: Android 12+ Bluetooth
- `USB_PERMISSION`: USB serial devices
- `ACCESS_FINE_LOCATION`: Bluetooth LE scanning
- `READ_EXTERNAL_STORAGE`: G-code file access

## License

MIT License - See LICENSE file for details

## Contributing

Contributions are welcome! Please read CONTRIBUTING.md for guidelines.

## Acknowledgments

- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) by mik3y
- [OpenCV](https://opencv.org/) for image processing
- [GRBL](https://github.com/gnea/grbl) for the excellent CNC firmware
- [FluidNC](https://github.com/bdring/FluidNC) for the advanced ESP32 firmware
