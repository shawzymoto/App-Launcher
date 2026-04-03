# Configuration Guide

How to configure and customize the App Launcher REST API.

## Table of Contents
1. [Changing the API Key](#changing-the-api-key)
2. [Adding Supported Apps](#adding-supported-apps)
3. [Modifying API Port](#modifying-api-port)
4. [Deep Link Configuration](#deep-link-configuration)

## Changing the API Key

### Default API Key
The default API key is: `app-launcher-default-key`

⚠️ **Security Warning**: Change this before deploying to production!

### Option 1: Through SharedPreferences (Android Settings)

The API key is stored in SharedPreferences at: `api_config.api_key`

You can change it programmatically or through a Settings UI.

### Option 2: Modify the Source Code

Edit [ApiKeyManager.kt](app/src/main/java/com/example/applauncher/ApiKeyManager.kt):

```kotlin
companion object {
    private const val API_KEY_PREF = "api_key"
    private const val API_KEY_DEFAULT = "your-new-secure-key-here"  // Change this
}
```

Then rebuild and install the app.

### Option 3: Runtime Configuration

You could add a Settings Activity (not included in basic version) to allow users to change the API key through the app UI.

### Using the API Key

Include the key in all API requests:

```bash
curl -X POST http://192.168.1.100:3001/api/launch/app.immich \
  -H "X-API-Key: your-new-secure-key-here"
```

### Generating a Secure Key

Generate a random 32-character key:

**Linux/Mac:**
```bash
openssl rand -hex 16
# Example output: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
```

**PowerShell (Windows):**
```powershell
-join((1..32) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) })
```

**Python:**
```python
import secrets
secrets.token_hex(16)
```

Then update `ApiKeyManager.kt` with the generated key.

## Adding Supported Apps

Supported apps are defined in [AppConfig.kt](app/src/main/java/com/example/applauncher/AppConfig.kt).

### Current Supported Apps

```kotlin
val SUPPORTED_APPS = listOf(
    SupportedApp(
        name = "Immich Frame",
        packageName = "app.immich",
        deepLinkScheme = "immich://",
        supportedActions = listOf("view", "refresh"),
        description = "Opens Immich photo frame application"
    ),
    SupportedApp(
        name = "Unifi Protect",
        packageName = "com.ubnt.android.protect",
        deepLinkScheme = "unifi-protect://",
        supportedActions = listOf("camera", "live-view", "event"),
        description = "Opens Unifi Protect security app, optionally to a specific camera view"
    )
)
```

### Adding a New App

Edit `AppConfig.kt` and add to the `SUPPORTED_APPS` list:

```kotlin
SupportedApp(
    name = "My Custom App",
    packageName = "com.example.myapp",
    deepLinkScheme = "myapp://",
    supportedActions = listOf("action1", "action2"),
    description = "Description of what this app does"
)
```

### Finding Package Names

To find an app's package name:

**Method 1: Android Settings**
1. Go to Settings > Apps
2. Find the app and view its details
3. Look for "Package name" field

**Method 2: Using adb**
```bash
# List all installed apps with package names
adb shell pm list packages

# Find specific app
adb shell pm list packages | grep -i "app-name"

# Example: Find Unifi Protect
adb shell pm list packages | grep -i "protect"
# Output: com.ubnt.android.protect
```

**Method 3: From Home Assistant**
Once the app is running, call:
```bash
curl http://192.168.1.100:3001/api/apps \
  -H "X-API-Key: app-launcher-default-key"
```

Find the app in the list and note its `packageName`.

### Finding Deep Link Schemes

To find an app's deep link scheme:

**Method 1: Check AndroidManifest.xml**
If you have the app's source code:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <scheme android:name="myapp" />
</intent-filter>
```

**Method 2: Use adb**
```bash
# Look for deep link schemes
adb shell dumpsys package resolvers | grep -A5 "myapp://"

# Or check the app's manifest
adb shell pm dump com.example.myapp | grep -i "scheme"
```

**Method 3: Check App Documentation**
Visit the app's GitHub or official website for deep link documentation.

### Example: Adding YouTube

```kotlin
SupportedApp(
    name = "YouTube",
    packageName = "com.google.android.youtube",
    deepLinkScheme = "youtube://",
    supportedActions = listOf("search", "watch", "playlist"),
    description = "Launch YouTube video player"
)
```

Then use:
```bash
# Simple launch
curl -X POST http://192.168.1.100:3001/api/launch/com.google.android.youtube \
  -H "X-API-Key: app-launcher-default-key"

# With deep link (play specific video)
curl -X POST http://192.168.1.100:3001/api/launch \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{
    "packageName": "com.google.android.youtube",
    "deepLink": "youtube://watch?v=dQw4w9WgXcQ"
  }'
```

### Example: Adding Home Assistant

```kotlin
SupportedApp(
    name = "Home Assistant",
    packageName = "io.homeassistant.companion.android",
    deepLinkScheme = "homeassistant://",
    supportedActions = listOf("open", "navigate"),
    description = "Open Home Assistant app"
)
```

## Modifying API Port

The REST API runs on port 3001 by default.

To change it, edit [ApiService.kt](app/src/main/java/com/example/applauncher/ApiService.kt):

```kotlin
companion object {
    private const val API_PORT = 3001  // Change this to another port
}
```

**Recommended alternative ports:**
- 8080 (common for web apps)
- 5000 (Flask default)
- 9000 (alternative high port)
- 3000 (Node.js default)

⚠️ **Note**: Ports below 1024 require root access on Android.

After changing, rebuild and install:
```bash
./gradlew installDebug
```

Update your Home Assistant configuration to use the new port:
```yaml
rest_command:
  launch_immich:
    url: "http://192.168.1.100:5000/api/launch/app.immich"  # New port
    method: POST
    headers:
      X-API-Key: "app-launcher-default-key"
```

## Deep Link Configuration

Deep links allow you to launch apps with specific parameters.

### Understanding Deep Links

Deep link format:
```
scheme://host/path?param1=value1&param2=value2
```

Example:
```
unifi-protect://camera/5f1a2b3c4d?view=fullscreen&quality=high
```

### Adding Deep Link Support to a New App

1. Find the app's deep link scheme
2. Learn the expected URL format from app documentation
3. Add to AppConfig:

```kotlin
SupportedApp(
    name = "Example App",
    packageName = "com.example.app",
    deepLinkScheme = "example://",
    supportedActions = listOf("open", "view"),
    description = "Example app with deep link support"
)
```

4. Use in API calls:

```bash
curl -X POST http://192.168.1.100:3001/api/launch \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{
    "packageName": "com.example.app",
    "deepLink": "example://page?param=value"
  }'
```

### Intent Extras

For apps that don't support deep links, use intent extras:

```bash
curl -X POST http://192.168.1.100:3001/api/launch \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{
    "packageName": "com.example.app",
    "action": "android.intent.action.VIEW",
    "extras": {
      "cameraId": "5f1a2b3c4d",
      "view": "fullscreen",
      "quality": "high"
    }
  }'
```

## Environment-Specific Configuration

### Development

Use default API key and port 3001:
```kotlin
private const val API_PORT = 3001
private const val API_KEY_DEFAULT = "dev-key-123456"
```

### Production

Use secure API key and consider changing port:
```kotlin
private const val API_PORT = 8080
private const val API_KEY_DEFAULT = "secure-random-key-32-chars"
```

## Rebuild and Deploy

After making configuration changes:

```bash
# Clean build
./gradlew clean build

# Build and install on device
./gradlew installDebug

# Or for release
./gradlew assembleRelease
```

## Verification

Test your configuration:

```bash
# Check API is running with new config
curl http://192.168.1.100:3001/health

# List supported apps (should show your new apps)
curl http://192.168.1.100:3001/api/apps \
  -H "X-API-Key: your-new-api-key"

# Test launching a new app
curl -X POST http://192.168.1.100:3001/api/launch/com.example.newapp \
  -H "X-API-Key: your-new-api-key"
```

## Common Configuration Tasks

### Change App Name Only

Edit `SUPPORTED_APPS` in `AppConfig.kt`:
```kotlin
SupportedApp(
    name = "My renamed app",  // Changed
    packageName = "com.ubnt.android.protect",
    // ... rest stays the same
)
```

### Add More Actions to an App

```kotlin
SupportedApp(
    name = "Unifi Protect",
    packageName = "com.ubnt.android.protect",
    deepLinkScheme = "unifi-protect://",
    supportedActions = listOf(
        "camera", 
        "live-view", 
        "event",
        "playback",    // New
        "settings"     // New
    ),
    description = "Opens Unifi Protect security app"
)
```

### Disable an App Temporarily

Remove it from the list temporarily, or comment it out:

```kotlin
val SUPPORTED_APPS = listOf(
    SupportedApp(
        name = "Immich Frame",
        packageName = "app.immich",
        // ...
    ),
    // Temporarily disabled:
    // SupportedApp( ... ),
)
```

## Configuration Best Practices

1. **Always change the default API key** for security
2. **Document your deep link schemes** - create a comment in AppConfig
3. **Test with curl first** before adding to Home Assistant
4. **Use version control** - commit config changes with `git`
5. **Backup your changes** - keep a copy of your modified files
6. **Test on emulator first** - before deploying to production device

## Troubleshooting Configuration

### App Not Appearing in API Response
- Verify packageName is correct
- Ensure app is installed on device
- Check app is in SUPPORTED_APPS list

### API Not Accessible After Changes
- Check for syntax errors in Kotlin code
- Verify port is not blocked by firewall
- Try rebuilding: `./gradlew clean build`

### Deep Links Not Working
- Verify app supports the deep link scheme
- Check deep link format matches app's expected format
- Test by launching app normally, then with link

### API Key Not Changing
- Rebuild and reinstall app: `./gradlew installDebug`
- Clear app data and try again
- Check SharedPreferences was updated correctly

## Next Steps

- [API Documentation](API_DOCUMENTATION.md) - Full API reference
- [Home Assistant Guide](HOME_ASSISTANT.md) - Integration with Home Assistant
- [Main README](README.md) - Project overview
