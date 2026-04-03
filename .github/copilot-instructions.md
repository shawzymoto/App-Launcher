# Android App Launcher Project

An Android application that displays installed apps and allows launching them locally or remotely via a REST API - perfect for Home Assistant integration.

## Project Overview
- **Language**: Kotlin
- **Build System**: Gradle
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14+
- **Architecture**: MVVM + REST API Server (Ktor)
- **API Framework**: Ktor (Kotlin)
- **API Port**: 3001 (configurable)

## Features

### UI Features
- Displays all installed user apps (non-system)
- Launch apps with a single tap
- App installation verification
- Real-time API status display

### REST API Features (★ NEW)
- Full REST API on port 3001
- Simple API key authentication
- List supported apps with installation status
- Launch apps by package name
- Deep link support for app-specific actions
- Intent extras for parameter passing
- Health check endpoint
- Configuration endpoint

### Home Assistant Integration (★ NEW)
- Ready-to-use integration with Home Assistant
- Automation support (motion detection, scheduled launches, etc.)
- Support for multi-device setups
- Deep link support for camera-specific views
- REST command examples included

## Key Components

### Core Components
- **MainActivity**: Displays app list and API status
- **AppLauncher**: Service for launching apps via intents
- **AppManager**: Queries installed apps from PackageManager

### New API Components (★ NEW)
- **ApiService**: Ktor-based REST API server
- **ApiKeyManager**: Manages API key authentication
- **AppConfig**: Defines supported apps and their actions
- **ApiModels**: Request/response data classes

## Supported Apps (Configurable)

Currently configured:
1. **Immich Frame** (`app.immich`) - Photo frame application
2. **Unifi Protect** (`com.ubnt.android.protect`) - Security camera app with deep link support for specific cameras

Can easily add more by editing `AppConfig.kt`

## Development Setup
1. Ensure Android SDK is installed (API level 26+)
2. Build with: `./gradlew build`
3. Deploy with: `./gradlew installDebug`
4. App automatically starts REST API server on port 3001

## REST API Examples

### Check API Status
```bash
curl http://192.168.1.100:3001/health
```

### List Supported Apps
```bash
curl http://192.168.1.100:3001/api/apps \
  -H "X-API-Key: app-launcher-default-key"
```

### Launch App
```bash
curl -X POST http://192.168.1.100:3001/api/launch/app.immich \
  -H "X-API-Key: app-launcher-default-key"
```

### Open Unifi Camera View
```bash
curl -X POST http://192.168.1.100:3001/api/launch \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{
    "packageName": "com.ubnt.android.protect",
    "deepLink": "unifi-protect://camera/CAMERA_ID"
  }'
```

## Home Assistant Integration

Add to Home Assistant `configuration.yaml`:
```yaml
rest_command:
  launch_unifi_protect:
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
      - service: rest_command.launch_unifi_protect
```

See [HOME_ASSISTANT.md](HOME_ASSISTANT.md) for full integration guide.

## File Structure

```
app/
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/applauncher/
│   │   │   ├── MainActivity.kt           # UI & API startup
│   │   │   ├── AppLauncher.kt            # App launching logic
│   │   │   ├── AppManager.kt             # App enumeration
│   │   │   ├── ApiService.kt             # REST API server ★ NEW
│   │   │   ├── ApiKeyManager.kt          # API authentication ★ NEW
│   │   │   ├── AppConfig.kt              # App configuration ★ NEW
│   │   │   └── ApiModels.kt              # API DTOs ★ NEW
│   │   └── res/
│   │       ├── layout/activity_main.xml
│   │       └── values/
│   │           ├── strings.xml
│   │           └── styles.xml
│   └── test/
└── build.gradle.kts

Root:
├── README.md                   # Project overview
├── API_DOCUMENTATION.md        # ★ NEW - Full API reference
├── HOME_ASSISTANT.md           # ★ NEW - Home Assistant guide
├── CONFIGURATION.md            # ★ NEW - Configuration guide
├── build.gradle.kts
├── settings.gradle.kts
└── .github/copilot-instructions.md
```

## Dependencies Added

### REST API (Ktor)
- `io.ktor:ktor-server-core:2.3.7`
- `io.ktor:ktor-server-cio:2.3.7`
- `io.ktor:ktor-server-content-negotiation:2.3.7`
- `io.ktor:ktor-serialization-kotlinx-json:2.3.7`

### Async Support
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3`

### Serialization
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0`

## Permissions

- `QUERY_ALL_PACKAGES` - List installed apps (Android 11+)
- `INTERNET` - REST API server
- `ACCESS_NETWORK_STATE` - Network operations

## API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/health` | GET | No | Health check |
| `/api/apps` | GET | Yes | List supported apps |
| `/api/launch/{packageName}` | POST | Yes | Launch app |
| `/api/launch` | POST | Yes | Launch with options |
| `/api/config` | GET | Yes | Get configuration |

Default API Key: `app-launcher-default-key`

See [API_DOCUMENTATION.md](API_DOCUMENTATION.md) for full details.

## Configuration

### Change API Key
Edit `ApiKeyManager.kt` or use the API configuration endpoint.
See [CONFIGURATION.md](CONFIGURATION.md#changing-the-api-key) for details.

### Add Supported Apps
Edit `AppConfig.kt` to add apps with package names and deep link schemes.
See [CONFIGURATION.md](CONFIGURATION.md#adding-supported-apps) for details.

### Change API Port
Edit `ApiService.kt` and update `API_PORT` constant.
See [CONFIGURATION.md](CONFIGURATION.md#modifying-api-port) for details.

## Security Notes

⚠️ **Important**: 
- Change the default API key before production deployment
- Use strong, random API keys (32 characters recommended)
- API is local-network only by default
- Never expose to public internet without additional auth

See [CONFIGURATION.md](CONFIGURATION.md#security-considerations) for security details.

## Unifi Protect Deep Links

To launch Unifi Protect and show a specific camera:

```json
{
  "packageName": "com.ubnt.android.protect",
  "deepLink": "unifi-protect://camera/CAMERA_ID"
}
```

Replace `CAMERA_ID` with your actual camera ID. Ways to find it:
1. Check Unifi Protect app or Unifi Controller
2. Monitor network requests in the app
3. Check Unifi API documentation

See [API_DOCUMENTATION.md](API_DOCUMENTATION.md#finding-unifi-camera-ids) for more details.

## Common Use Cases

1. **Launch Immich Photo Frame on schedule** - See [HOME_ASSISTANT.md](HOME_ASSISTANT.md#time-based-automation)
2. **Show Unifi camera on motion detected** - See [HOME_ASSISTANT.md](HOME_ASSISTANT.md#motion-detection-automation)
3. **Open specific camera on door open** - See [HOME_ASSISTANT.md](HOME_ASSISTANT.md#door-open-detection)
4. **Create automation scripts** - See [HOME_ASSISTANT.md](HOME_ASSISTANT.md#script-examples)

## Troubleshooting

### REST API Not Starting
- Check Android logs: `adb logcat | grep ApiService`
- Verify app has INTERNET permission
- Ensure port 3001 is not in use

### API Key Issues
- Default key: `app-launcher-default-key`
- Verify header spelling: `X-API-Key`
- Check no typos or extra spaces

### Deep Links Not Working
- Verify app supports deep links
- Test deep link format
- Check app is installed and works manually first

See project documentation for full troubleshooting:
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md#troubleshooting)
- [HOME_ASSISTANT.md](HOME_ASSISTANT.md#troubleshooting)
- [CONFIGURATION.md](CONFIGURATION.md#troubleshooting-configuration)

## Documentation Files

- **README.md** - Project overview and feature list
- **API_DOCUMENTATION.md** - Complete REST API reference with examples
- **HOME_ASSISTANT.md** - Home Assistant integration guide with examples
- **CONFIGURATION.md** - Configuration, customization, and setup guide

## Next Steps

1. Build and deploy: `./gradlew installDebug`
2. Check API is running: `curl http://device-ip:3001/health`
3. List apps: `curl http://device-ip:3001/api/apps -H "X-API-Key: app-launcher-default-key"`
4. Add to Home Assistant: See [HOME_ASSISTANT.md](HOME_ASSISTANT.md)
5. Customize: See [CONFIGURATION.md](CONFIGURATION.md)

## Development Notes

- API runs on port 3001 by default (configurable)
- Uses Ktor CIO engine (lightweight, no separate server)
- Coroutine-based async/await patterns
- JSON serialization with kotlinx-serialization
- Local network only (security by default)

## Future Enhancements

- HTTPS/TLS support
- OAuth2 authentication
- App launch history/logging
- Rate limiting
- Websocket for real-time status
- Auto-discovery of Deep links
- Schedule app launches
- Multiple API key support

