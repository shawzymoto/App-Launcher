#!/bin/zsh

set -e

# Pass --emulator to also install the emulator and create an AVD.
# Default (no flag) installs only what's needed to build and deploy to a physical device.
EMULATOR_MODE=false
for arg in "$@"; do
  if [[ "$arg" == "--emulator" ]]; then
    EMULATOR_MODE=true
  fi
done

echo "================================"
echo "Android Development Setup Script"
echo "================================"
echo ""

# Step 1: Install Java 21
echo "Step 1: Installing Java 21 JDK..."
sudo apt update
sudo apt install -y openjdk-21-jdk
java -version
echo "✓ Java 21 installed"
echo ""

# Step 2: Create Android SDK directory
echo "Step 2: Setting up Android SDK directory..."
mkdir -p ~/Android/Sdk
echo "✓ Android SDK directory created"
echo ""

# Step 3: Download and extract Android CLI tools
echo "Step 3: Downloading Android CLI tools (this may take a minute)..."
cd ~/Downloads
rm -f commandlinetools-linux-*_latest.zip(N) 2>/dev/null; true
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
echo "✓ Downloaded CLI tools"

echo "Extracting..."
unzip -q -o commandlinetools-linux-*_latest.zip
rm -f commandlinetools-linux-*_latest.zip

# Organize CLI tools properly
mkdir -p ~/Android/Sdk/cmdline-tools/latest
cp -rf cmdline-tools/* ~/Android/Sdk/cmdline-tools/latest/ 2>/dev/null; true
rm -rf cmdline-tools
echo "✓ CLI tools extracted and organized"
echo ""

# Step 4: Set up environment variables
echo "Step 4: Configuring environment variables..."
if ! grep -q 'ANDROID_HOME' ~/.zshrc; then
  cat >> ~/.zshrc << 'EOF'

# Android SDK Configuration
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH
EOF
  echo "✓ Environment variables added to .zshrc"
else
  echo "✓ Environment variables already configured, skipping"
fi
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH
echo ""

# Step 5: Accept licenses
echo "Step 5: Accepting Android SDK licenses..."
yes | sdkmanager --licenses 2>/dev/null || true
echo "✓ Licenses accepted"
echo ""

# Step 6: Install SDK components
echo "Step 6: Cleaning up any partial/interrupted SDK downloads..."
rm -rf ~/Android/Sdk/.temp ~/Android/Sdk/temp 2>/dev/null || true
find ~/Android/Sdk -name '*.part' -delete 2>/dev/null || true
find ~/Android/Sdk -name '*.tmp' -delete 2>/dev/null || true
echo "✓ Cleaned up partial downloads"

if [[ "$EMULATOR_MODE" == true ]]; then
  echo "Installing SDK components including emulator and system image (~1.5GB)..."
  yes | sdkmanager \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "emulator" \
    "platform-tools" \
    "system-images;android-34;google_apis_playstore;x86_64"
else
  echo "Installing SDK components (physical device mode — no emulator/system image)..."
  yes | sdkmanager \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "platform-tools"
fi
echo "✓ SDK components installed"
echo ""

# Step 7: Install Gradle and generate project wrapper
echo "Step 7: Installing Gradle 8.5 and generating project Gradle wrapper..."
# NOTE: the apt version of gradle is broken on Ubuntu - must download directly from gradle.org
if [[ ! -f ~/gradle/gradle-8.5/bin/gradle ]]; then
  cd ~/Downloads
  rm -f gradle-8.5-bin.zip 2>/dev/null; true
  wget -q https://services.gradle.org/distributions/gradle-8.5-bin.zip
  unzip -q -o gradle-8.5-bin.zip -d ~/gradle
  rm -f gradle-8.5-bin.zip
  echo "✓ Gradle 8.5 downloaded"
else
  echo "✓ Gradle 8.5 already present, skipping download"
fi

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"
~/gradle/gradle-8.5/bin/gradle wrapper --gradle-version 8.9
chmod +x gradlew
echo "✓ Gradle wrapper generated (targets Gradle 8.9)"
echo ""

# Step 8: Create emulator (only if --emulator flag was passed)
if [[ "$EMULATOR_MODE" == true ]]; then
  echo "Step 8: Creating Android emulator 'pixel-emulator'..."
  avdmanager delete avd -n pixel-emulator > /dev/null 2>&1 || true
  avdmanager create avd \
    -n pixel-emulator \
    -k "system-images;android-34;google_apis_playstore;x86_64" \
    -d pixel \
    -c 2048M \
    -f
  echo "✓ Emulator created"
else
  echo "Step 8: Skipping emulator creation (use --emulator flag to enable)"
fi
echo ""

# Step 9: Build the app
echo "Step 9: Building the app..."
cd "$PROJECT_DIR"
./gradlew assembleDebug
echo "✓ App built successfully"
echo ""

echo "================================"
echo "✓ Setup Complete!"
echo "================================"
echo ""
echo "Next steps:"
echo ""
echo "1. Enable USB Debugging on your Android device:"
echo "   Settings → About Phone → tap Build Number 7 times"
echo "   Settings → Developer Options → enable USB Debugging"
echo ""
echo "2. Plug in device via USB, then verify adb sees it:"
echo "   adb devices"
echo ""
echo "3. Deploy and test the app:"
echo "   ./scripts/test-emulator-api.sh"
echo ""
if [[ "$EMULATOR_MODE" == true ]]; then
echo "4. Or start the emulator:"
echo "   ./scripts/start-emulator-fast.sh"
echo ""
fi
