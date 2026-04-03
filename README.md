# Droid Controller

An Android application that displays installed apps and allows launching them locally or remotely via a REST API — designed for Home Assistant and automation integration.

## Features

- 📱 Display all installed user apps
- ⚡ Launch apps with a single tap
- 🌐 REST API server on port 3001 (auto-starts with app)
- 🔑 API key authentication
- 🏠 Home Assistant integration ready
- 🔗 Deep link support (e.g. open a specific Unifi camera view)

## Development Setup (Linux)

### Prerequisites

- Ubuntu/Debian Linux
- ~5GB free disk space (Android SDK + system image)
- Internet connection

### One-Command Setup

A setup script is included in the repo at `setup-android.sh`. It handles everything from scratch:

```bash
# Clone the repo, then:
chmod +x setup-android.sh

# Physical device (default) — skips emulator/system image (~4GB saved)
./setup-android.sh

# Emulator — installs system image and creates AVD
./setup-android.sh --emulator
```

**What it does (automatically):**
1. Installs Java 21 JDK (`sudo apt install openjdk-21-jdk`)
2. Downloads Android SDK command-line tools
3. Configures `ANDROID_HOME` in `~/.zshrc`
4. Accepts SDK licenses
5. Installs Android API 34, build tools, emulator, and x86_64 system image (~1.5GB download)
6. Downloads Gradle 8.5 to `~/gradle/gradle-8.5/` (the apt version is broken — do not use it)
7. Generates the Gradle wrapper in this project (targets Gradle 8.9)
8. Creates a `pixel-emulator` AVD
9. Builds the app (`./gradlew assembleDebug`)

> ⚠️ **Note:** Do NOT use `sudo apt install gradle` — it installs a broken version that cannot find its own main class. The script downloads Gradle directly from gradle.org instead.

### Build Versions

| Component | Version |
|-----------|---------|
| Android Gradle Plugin | 8.7.0 |
| Gradle | 8.9 |
| Kotlin | 2.0.0 |
| Java | 21 |
| Compile SDK | 34 |
| Min SDK | 26 (Android 8.0) |

### Running on Emulator

```bash
# Terminal 1: Start the emulator
emulator -avd pixel-emulator -no-snapshot-load

# Terminal 2: Deploy the app (after emulator boots ~30s)
cd ~/development/droid\ controller
./gradlew installDebug

# Set up port forwarding so your host machine can reach the API
adb forward tcp:3001 tcp:3001
```

For slower machines, use the low-overhead emulator launcher:

```bash
./scripts/start-emulator-fast.sh
```

### Repeat Test Command

For day-to-day testing, use the helper script that performs install + app restart + adb forward + health check:

```bash
./scripts/test-emulator-api.sh
```

This script now waits for device boot completion and retries health checks for up to ~60 seconds,
which helps avoid false failures on slow emulators.

Optional: skip reinstall when you only want to restart and re-check API health:

```bash
./scripts/test-emulator-api.sh --skip-install
```

## REST API Quick Start

The app starts a REST API on port 3001 automatically when launched.

**From Postman or curl on your host machine (after `adb forward`):**

```bash
# Health check (no auth required)
curl http://localhost:3001/health

# List configured apps
curl http://localhost:3001/api/apps \
  -H "X-API-Key: app-launcher-default-key"

# Launch an app
curl -X POST http://localhost:3001/api/launch/app.immich \
  -H "X-API-Key: app-launcher-default-key"

# Launch with deep link (e.g. specific Unifi camera)
curl -X POST http://localhost:3001/api/launch \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{"packageName": "com.ubnt.android.protect", "deepLink": "unifi-protect://camera/CAMERA_ID"}'
```

**On a physical device or real network:** replace `localhost` with the device's IP address. No `adb forward` needed.

👉 See [API_DOCUMENTATION.md](API_DOCUMENTATION.md) for full endpoint reference  
👉 See [HOME_ASSISTANT.md](HOME_ASSISTANT.md) for automation examples  
👉 See [CONFIGURATION.md](CONFIGURATION.md) for changing API key, port, and adding apps

## Supported Apps

The following apps are currently supported for remote launching:

| App Name | Package | Actions | Notes |
|----------|---------|---------|-------|
| **Immich Frame** | `app.immich` | view, refresh | Photo frame app |
| **Unifi Protect** | `com.ubnt.android.protect` | camera, live-view, event | Security camera app with camera-specific deep link support |

## Project Structure

```
AppLauncher/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/example/applauncher/
│   │   │   │   ├── MainActivity.kt               # Main activity
│   │   │   │   ├── AppLauncher.kt                # App launching logic
│   │   │   │   ├── AppManager.kt                 # Installed apps query
│   │   │   │   ├── ApiService.kt                 # REST API server ★ NEW
│   │   │   │   ├── ApiKeyManager.kt              # API key management ★ NEW
│   │   │   │   ├── AppConfig.kt                  # Supported apps config ★ NEW
│   │   │   │   └── ApiModels.kt                  # API data classes ★ NEW
│   │   │   └── res/
│   │   │       ├── layout/
│   │   │       │   └── activity_main.xml         # Main UI layout
│   │   │       └── values/
│   │   │           ├── strings.xml               # String resources
│   │   │           └── styles.xml                # Theme and colors
│   │   └── test/
│   └── build.gradle.kts
├── settings.gradle.kts
├── build.gradle.kts
├── README.md
├── API_DOCUMENTATION.md                          # ★ NEW - Full API docs
└── .gitignore
```

## Architecture

### Traditional UI Flow
1. **MainActivity** displays installed apps
2. User taps an app to launch it
3. **AppLauncher** handles the intent

### New REST API Flow
1. **ApiService** listens on port 3001
2. External client sends HTTP request with API key
3. **AppLauncher** handles the intent
4. Response sent back over HTTP

### Components

- **MainActivity**: UI Layer - Displays app list and API status
- **AppLauncher**: Business Logic - Handles launching apps via intents
- **AppManager**: Data Layer - Queries installed apps
- **ApiService**: API Layer - Ktor-based REST API server ★ NEW
- **ApiKeyManager**: Security - Manages API authentication ★ NEW
- **AppConfig**: Configuration - Defines supported apps ★ NEW

## Requirements

- Android 8.0 (API 26) or higher
- Android SDK 34+
- Kotlin 1.9.20+
- Gradle 8.2.0+

## Development Setup

### Prerequisites
1. Install [Android Studio](https://developer.android.com/studio)
2. Install Android SDK (API level 26 or higher)
3. Ensure Java 17+ is installed

### Build and Run

```bash
# Build the project
./gradlew build

# Run on an emulator or device
./gradlew installDebug

# Run tests
./gradlew test
```

## Permissions

The app requests the following permissions:
- `QUERY_ALL_PACKAGES`: Required to query and display installed applications (Android 11+)
- `INTERNET`: Required for REST API server
- `ACCESS_NETWORK_STATE`: Required for network operations

## REST API Overview

### Authentication
All endpoints require an API key header (except `/health`):
```
X-API-Key: app-launcher-default-key
```

### Main Endpoints
- `GET /health` - Health check (no auth)
- `GET /api/apps` - List supported and installed apps
- `POST /api/launch/{packageName}` - Launch app by package name
- `POST /api/launch` - Launch with deep link or intent extras
- `GET /api/config` - Get server configuration

See [API_DOCUMENTATION.md](API_DOCUMENTATION.md) for full details.

## Home Assistant Integration

Add to your `configuration.yaml`:

```yaml
rest_command:
  launch_unifi:
    url: "http://192.168.1.100:3001/api/launch/com.ubnt.android.protect"
    method: POST
    headers:
      X-API-Key: "app-launcher-default-key"

automation:
  - alias: "Show Unifi on Motion"
    trigger:
      - platform: state
        entity_id: binary_sensor.motion_detected
        to: 'on'
    action:
      - service: rest_command.launch_unifi
```

See [API_DOCUMENTATION.md](API_DOCUMENTATION.md) for more Home Assistant examples.

## How It Works

### App Discovery & Launch
1. When the app starts, `AppManager` queries the package manager
2. `ListView` displays installed user apps
3. When tapped, `AppLauncher` fetches the launch intent and starts the activity

### REST API
1. `ApiService` starts a Ktor server on port 3001
2. Requests are validated with API key authentication
3. `AppLauncher` or Intent APIs handle app launching
4. Response is sent back as JSON

## Customization

You can customize the app by:

### Adding More Supported Apps
Edit `AppConfig.kt` and add to `SUPPORTED_APPS`:
```kotlin
SupportedApp(
    name = "My App",
    packageName = "com.example.myapp",
    deepLinkScheme = "myapp://",
    supportedActions = listOf("action1", "action2")
)
```

### Changing API Key
Modify `ApiKeyManager.kt` or use the SharedPreferences directly:
```
SharedPreferences api_config: api_key = "your-custom-key"
```

### Other Customizations
- Show app icons alongside names in the list
- Add filter options (system apps, categories)
- Implement app search/filter
- Add sorting options (alphabetical, recently used)
- Custom deep link schemes for each app

## Security Notes

⚠️ **Important**: Change the default API key before deploying!

The default API key is `app-launcher-default-key`. In a real deployment:
1. Use a strong, random API key
2. Restrict network access to trusted devices/networks
3. Consider using HTTPS (requires certificate setup)
4. Never expose the API to the public internet without authentication

## API Response Examples

### Success Response
```json
{
  "success": true,
  "message": "App launched successfully",
  "data": {
    "packageName": "app.immich"
  }
}
```

### Error Response
```json
{
  "success": false,
  "message": "Unauthorized: Invalid or missing API key",
  "error": "INVALID_API_KEY"
}
```

## Troubleshooting

### REST API Not Starting
- Check Android app logs: `adb logcat | grep ApiService`
- Verify app has INTERNET permission
- Ensure port 3001 is not in use

### API Key Issues
- Default key: `app-launcher-default-key`
- Verify inclusion in `X-API-Key` header
- Check exact spelling and case

### Deep Link Not Working
- Verify app supports deep links
- Test deep link scheme format
- Check app is installed and working

For detailed API troubleshooting, see [API_DOCUMENTATION.md](API_DOCUMENTATION.md#troubleshooting).

## Future Enhancements

- [ ] HTTPS/TLS support
- [ ] OAuth2 authentication
- [ ] App launch history and logging
- [ ] Rate limiting per API key
- [ ] Advanced configuration UI
- [ ] Websocket support for real-time app status
- [ ] Discover Unifi Protect camera IDs automatically
- [ ] Schedule app launches via API

## License

This project is provided as-is for educational purposes.
