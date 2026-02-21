# GitHub Setup & Build Guide for Titan CNC

Panduan lengkap untuk setup repository GitHub dan build aplikasi Titan CNC secara otomatis.

## 📁 Struktur Repository

```
TitanCNC/
├── .github/
│   └── workflows/
│       ├── build.yml      # Build otomatis setiap push
│       └── release.yml    # Release dengan signed APK
├── app/
│   ├── build.gradle.kts
│   └── src/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── gradle.properties
├── local.properties (tidak di-commit)
└── README.md
```

## 🚀 Setup Repository GitHub

### 1. Buat Repository Baru

```bash
# Di GitHub, buat repository baru dengan nama "TitanCNC"
# Jangan initialize dengan README (sudah ada)
```

### 2. Push Project ke GitHub

```bash
# Di folder project TitanCNC

# Initialize git
git init

# Add semua files
git add .

# Commit
git commit -m "Initial commit: Titan CNC v1.0.0"

# Add remote repository
git remote add origin https://github.com/YOUR_USERNAME/TitanCNC.git

# Push ke main branch
git branch -M main
git push -u origin main
```

### 3. Setup GitHub Actions Secrets (Untuk Signed Release)

Untuk membuat APK yang signed, tambahkan secrets berikut:

#### Buat Keystore Baru

```bash
# Generate keystore untuk signing APK
keytool -genkey -v \
  -keystore titancnc-keystore.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias titancnc

# Encode keystore ke base64
base64 -w 0 titancnc-keystore.jks > keystore-base64.txt
```

#### Tambahkan Secrets di GitHub

1. Buka repository GitHub → Settings → Secrets and variables → Actions
2. Klik "New repository secret"
3. Tambahkan secrets berikut:

| Secret Name | Value |
|-------------|-------|
| `KEYSTORE_BASE64` | Isi dari file `keystore-base64.txt` |
| `KEYSTORE_PASSWORD` | Password keystore yang Anda buat |
| `KEY_ALIAS` | Alias key (misal: titancnc) |
| `KEY_PASSWORD` | Password key |

## 🔧 Build Otomatis dengan GitHub Actions

### Build Debug APK (Setiap Push)

Setiap kali Anda push ke branch `main` atau `develop`, GitHub Actions akan:
1. Build Debug APK
2. Run unit tests
3. Upload APK sebagai artifact

**Download APK:**
- Buka tab "Actions" di repository
- Pilih workflow "Build Titan CNC"
- Klik artifact "TitanCNC-Debug-APK"

### Build Release APK (Manual/Tag)

#### Opsi 1: Push Tag

```bash
# Buat tag baru
git tag -a v1.0.0 -m "Release version 1.0.0"

# Push tag
git push origin v1.0.0
```

GitHub Actions akan otomatis:
1. Build signed release APK
2. Create GitHub Release
3. Upload APK ke release

#### Opsi 2: Manual Trigger

1. Buka tab "Actions" di repository
2. Pilih workflow "Release Titan CNC"
3. Klik "Run workflow"
4. Masukkan version number
5. Klik "Run workflow"

## 🖥️ Build Lokal (Setelah Clone dari GitHub)

### Prerequisites

- Android Studio Hedgehog (2023.1.1) atau lebih baru
- JDK 17
- Android SDK 35
- NDK (untuk OpenCV native code)

### Clone Repository

```bash
# Clone dari GitHub
git clone https://github.com/YOUR_USERNAME/TitanCNC.git

# Masuk ke folder
cd TitanCNC
```

### Build dengan Android Studio

1. **Open Project**
   - Buka Android Studio
   - File → Open → Pilih folder `TitanCNC`
   - Tunggu Gradle sync selesai

2. **Sync Gradle**
   - Klik "Sync Now" jika diminta
   - Atau: File → Sync Project with Gradle Files

3. **Build APK**
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Atau tekan: `Ctrl+F9` (Windows/Linux) atau `Cmd+F9` (Mac)

4. **Install ke Device**
   - Hubungkan Android device via USB
   - Enable USB Debugging di device
   - Klik "Run" (▶) atau tekan `Shift+F10`

### Build dengan Command Line

```bash
# Grant permission ke gradlew
chmod +x gradlew

# Build Debug APK
./gradlew assembleDebug

# Build Release APK (unsigned)
./gradlew assembleRelease

# Build dan install ke device yang terhubung
./gradlew installDebug

# Run tests
./gradlew test

# Clean build
./gradlew clean

# Full rebuild
./gradlew clean build
```

### Lokasi Output APK

| Build Type | Lokasi |
|------------|--------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release-unsigned.apk` |

## 📋 Troubleshooting

### Gradle Sync Failed

```bash
# Clear Gradle cache
./gradlew cleanBuildCache

# Delete .gradle folder
rm -rf ~/.gradle/caches/

# Re-sync
./gradlew --refresh-dependencies
```

### OpenCV Not Found

```bash
# Pastikan OpenCV library tersedia
# Di build.gradle.kts sudah include:
# implementation("org.opencv:opencv:4.9.0")
```

### NDK Not Found

1. Buka SDK Manager di Android Studio
2. Tab "SDK Tools"
3. Centang "NDK (Side by side)"
4. Klik "Apply"

### Out of Memory Error

```bash
# Tambahkan di gradle.properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.configureondemand=true
```

## 🔄 Update Dependencies

```bash
# Check for dependency updates
./gradlew dependencyUpdates

# Update version di gradle/libs.versions.toml
# Lalu sync project
```

## 📦 Membuat Release Baru

### Versioning (Semantic Versioning)

Format: `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes

### Release Checklist

```bash
# 1. Update version di app/build.gradle.kts
versionCode = 2
versionName = "1.1.0"

# 2. Update CHANGELOG.md

# 3. Commit changes
git add .
git commit -m "Prepare release v1.1.0"

# 4. Create tag
git tag -a v1.1.0 -m "Release v1.1.0"

# 5. Push
git push origin main
git push origin v1.1.0

# 6. GitHub Actions akan otomatis build dan create release
```

## 🐳 Build dengan Docker (Optional)

```dockerfile
# Dockerfile
FROM openjdk:17-jdk-slim

RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    git

# Download Android SDK
ENV ANDROID_SDK_ROOT=/opt/android-sdk
RUN mkdir -p ${ANDROID_SDK_ROOT}
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
RUN unzip commandlinetools-linux-9477386_latest.zip -d ${ANDROID_SDK_ROOT}
RUN rm commandlinetools-linux-9477386_latest.zip

ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/bin

# Accept licenses dan install SDK
RUN yes | sdkmanager --licenses
RUN sdkmanager "platforms;android-35" "build-tools;35.0.0"

WORKDIR /app
COPY . .

RUN ./gradlew assembleDebug
```

```bash
# Build dengan Docker
docker build -t titancnc-builder .
docker run -v $(pwd)/output:/app/app/build/outputs titancnc-builder
```

## 📚 Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Android CI/CD with GitHub Actions](https://developer.android.com/studio/build/building-cmdline)
- [Gradle Build Tool](https://gradle.org/guides/)

---

## 💡 Tips

1. **Enable Gradle Daemon**: Mempercepat build berikutnya
2. **Use Build Cache**: `./gradlew assembleDebug --build-cache`
3. **Parallel Build**: Sudah di-enable di `gradle.properties`
4. **Exclude Tests**: `./gradlew assembleDebug -x test` (untuk build lebih cepat)

---

**Selamat membangun!** 🚀
