# Titan CNC - Quick Start Guide

## 🎯 Cara Paling Cepat (GitHub Actions)

### 1. Fork Repository

```bash
# Klik tombol "Fork" di GitHub
# Atau clone dan push ke repo baru
```

### 2. GitHub Actions Otomatis Build

Setiap push ke `main` atau `develop` akan otomatis build:

1. Buka tab **Actions** di repository GitHub
2. Pilih workflow **"Build Titan CNC"**
3. Klik artifact **"TitanCNC-Debug-APK"** untuk download

### 3. Install APK ke Android

```bash
# Enable USB Debugging di Android
# Connect via USB
adb install app-debug.apk
```

---

## 📦 Build Lokal (Command Line)

```bash
# 1. Clone repo
git clone https://github.com/YOUR_USERNAME/TitanCNC.git
cd TitanCNC

# 2. Grant permission
chmod +x gradlew

# 3. Build Debug APK
./gradlew assembleDebug

# 4. APK tersedia di:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔐 Setup Signed Release (Optional)

### 1. Buat Keystore

```bash
keytool -genkey -v \
  -keystore titancnc-keystore.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias titancnc
```

### 2. Encode ke Base64

```bash
# Linux/Mac
base64 -w 0 titancnc-keystore.jks > keystore-base64.txt

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("titancnc-keystore.jks")) | Out-File -Encoding ASCII keystore-base64.txt
```

### 3. Tambah Secrets di GitHub

Buka: `Settings` → `Secrets and variables` → `Actions` → `New repository secret`

| Secret Name | Value |
|-------------|-------|
| `KEYSTORE_BASE64` | Copy isi `keystore-base64.txt` |
| `KEYSTORE_PASSWORD` | Password yang Anda buat |
| `KEY_ALIAS` | `titancnc` |
| `KEY_PASSWORD` | Password key |

### 4. Release Otomatis

```bash
# Buat tag baru
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0

# GitHub Actions akan otomatis:
# 1. Build signed APK
# 2. Create GitHub Release
# 3. Upload APK ke release
```

---

## 🖥️ Build dengan Android Studio

### Setup

1. **Install Android Studio** (Hedgehog atau lebih baru)
2. **Install Plugin**: Kotlin, Android

### Build Steps

1. **Open Project**
   ```
   File → Open → Pilih folder TitanCNC
   ```

2. **Sync Gradle**
   ```
   File → Sync Project with Gradle Files
   ```

3. **Build APK**
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```

4. **Run di Device**
   ```
   Run → Run 'app' (atau tekan Shift+F10)
   ```

---

## 🔧 Troubleshooting

### Gradle Sync Failed

```bash
# Clear cache
./gradlew cleanBuildCache
rm -rf ~/.gradle/caches/

# Re-sync
./gradlew --refresh-dependencies
```

### Out of Memory

Tambahkan di `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m
org.gradle.parallel=true
org.gradle.caching=true
```

### NDK Not Found

1. SDK Manager → SDK Tools
2. Centang "NDK (Side by side)"
3. Apply

---

## 📱 Install ke Android

### Via USB

```bash
# Enable Developer Options → USB Debugging
adb devices                    # Cek device terdeteksi
adb install app-debug.apk      # Install APK
```

### Via File Transfer

1. Copy APK ke Android
2. Enable "Install from Unknown Sources"
3. Tap APK untuk install

---

## 🚀 Commands Cheat Sheet

```bash
# Build
./gradlew assembleDebug        # Debug APK
./gradlew assembleRelease      # Release APK
./gradlew build                # Full build

# Test
./gradlew test                 # Run unit tests
./gradlew connectedAndroidTest # Run instrumented tests

# Clean
./gradlew clean                # Clean build files
./gradlew clean build          # Full rebuild

# Install
./gradlew installDebug         # Install debug ke device
./gradlew installRelease       # Install release ke device

# Check
./gradlew lint                 # Run lint checks
./gradlew dependencyUpdates    # Check dependency updates
```

---

## 📂 Project Structure

```
TitanCNC/
├── .github/workflows/     # CI/CD configs
├── app/
│   ├── src/main/
│   │   ├── java/          # Kotlin source
│   │   ├── cpp/           # Native code
│   │   └── res/           # Resources
│   └── build.gradle.kts   # App build config
├── gradle/
│   └── libs.versions.toml # Version catalog
├── build.gradle.kts       # Root build config
└── gradlew               # Gradle wrapper
```

---

## 💡 Tips

1. **Build lebih cepat**:
   ```bash
   ./gradlew assembleDebug --build-cache --parallel
   ```

2. **Skip tests** (untuk development):
   ```bash
   ./gradlew assembleDebug -x test
   ```

3. **Incremental build**:
   ```bash
   ./gradlew assembleDebug --incremental
   ```

---

**Selamat membangun!** 🎉
