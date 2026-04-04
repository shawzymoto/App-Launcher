# REST API Documentation - App Launcher

This document describes the REST API endpoints available for controlling app launching remotely via HTTP requests. Perfect for integrating with Home Assistant.

## Base URL
```
http://<device-ip>:3001
```

## Authentication
All endpoints (except `/health`) require an API key header:
```
X-API-Key: <api-key>
```

The default API key is: `app-launcher-default-key`

**Change this in your Android app for security!**

## Endpoints

### 1. Health Check (No Auth Required)
Check if the API server is running.

**Request:**
```
GET /health
```

**Response:**
```json
{
  "success": true,
  "message": "API Service is running",
  "data": {
    "port": 3001,
    "version": "1.0"
  }
}
```

---

### 2. List Installed Launchable Apps
Get a list of installed apps that can be launched by package name.

**Request:**
```
GET /api/apps
Headers:
  X-API-Key: app-launcher-default-key
```

**Response:**
```json
{
  "success": true,
  "message": "Installed launchable apps retrieved",
  "data": {
    "apps": [
      {
        "appName": "Immich Frame",
        "packageName": "com.immichframe.immichframe",
        "supported": true,
        "supportedActions": []
      },
      {
        "appName": "Unifi Protect",
        "packageName": "com.ubnt.android.protect",
        "supported": true,
        "supportedActions": []
      }
    ]
  }
}
```

---

### 3. Launch App (Simple)
Launch an app by its package name.

**Request:**
```
POST /api/launch/{packageName}
Headers:
  X-API-Key: app-launcher-default-key
```

**Examples:**
```bash
# Launch any installed app by package name
curl -X POST http://192.168.1.100:3001/api/launch/com.immichframe.immichframe \
  -H "X-API-Key: app-launcher-default-key"

# Launch Unifi Protect
curl -X POST http://192.168.1.100:3001/api/launch/com.ubnt.android.protect \
  -H "X-API-Key: app-launcher-default-key"
```

**Response:**
```json
{
  "success": true,
  "message": "App launched successfully",
  "data": {
    "packageName": "com.immichframe.immichframe"
  }
}
```

---

### 4. Launch App with Options
Launch an app with deep links or intent extras for more control.

**Request:**
```
POST /api/launch
Headers:
  X-API-Key: app-launcher-default-key
Content-Type: application/json

Body:
{
  "packageName": "com.ubnt.android.protect",
  "action": "VIEW",
  "deepLink": "unifi-protect://camera/cameraid",
  "extras": {
    "cameraId": "cameraid",
    "timestamp": "1234567890"
  }
}
```

**Parameters:**
- `packageName` (required): The package name of the app
- `action` (optional): Intent action (e.g., "VIEW", "CALL")
- `deepLink` (optional): Deep link URL to pass to the app
- `extras` (optional): Key-value pairs to pass as intent extras

**Example with Deep Link:**
```bash
curl -X POST http://192.168.1.100:3001/api/launch \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{
    "packageName": "com.ubnt.android.protect",
    "deepLink": "unifi-protect://camera/5f1a2b3c4d"
  }'
```

**Response:**
```json
{
  "success": true,
  "message": "App launched with specified options",
  "data": {
    "packageName": "com.ubnt.android.protect",
    "action": "default"
  }
}
```

---

### 5. Get API Configuration
Retrieve the current API configuration and installed app metadata snapshot.

**Request:**
```
GET /api/config
Headers:
  X-API-Key: app-launcher-default-key
```

**Response:**
```json
{
  "success": true,
  "message": "Configuration retrieved",
  "data": {
    "port": 3001,
    "apiKeyRequired": true,
    "supportedApps": [
      {
        "name": "Immich Frame",
        "packageName": "com.immichframe.immichframe",
        "deepLinkScheme": null,
        "supportedActions": [],
        "description": "Installed app"
      },
      {
        "name": "Unifi Protect",
        "packageName": "com.ubnt.android.protect",
        "deepLinkScheme": null,
        "supportedActions": [],
        "description": "Installed app"
      }
    ]
  }
}
```

---

### 6. Get Quiet Hours Status
Retrieve quiet-hours settings and whether quiet mode is currently active.

**Request:**
```
GET /api/quiet-hours
Headers:
  X-API-Key: app-launcher-default-key
```

**Response:**
```json
{
  "success": true,
  "message": "Quiet hours status retrieved",
  "data": {
    "enabled": true,
    "startHour": 22,
    "startMinute": 0,
    "endHour": 7,
    "endMinute": 0,
    "activeNow": false
  }
}
```

---

### 7. Update Quiet Hours Settings
Update one or more quiet-hours fields in a single request.

**Request:**
```
POST /api/quiet-hours
Headers:
  X-API-Key: app-launcher-default-key
Content-Type: application/json

Body:
{
  "enabled": true,
  "startHour": 22,
  "startMinute": 0,
  "endHour": 7,
  "endMinute": 0
}
```

All fields are optional. Time values must be valid 24-hour values (`0-23` for hour, `0-59` for minute).

**Response:**
```json
{
  "success": true,
  "message": "Quiet hours updated",
  "data": {
    "enabled": true,
    "startHour": 22,
    "startMinute": 0,
    "endHour": 7,
    "endMinute": 0,
    "activeNow": false
  }
}
```

---

### 8. Toggle Quiet Hours Enabled State
Quickly enable or disable quiet hours without changing times.

**Request:**
```
POST /api/quiet-hours/enabled
Headers:
  X-API-Key: app-launcher-default-key
Content-Type: application/json

Body:
{
  "enabled": false
}
```

**Response:**
```json
{
  "success": true,
  "message": "Quiet hours enabled state updated",
  "data": {
    "enabled": false,
    "startHour": 22,
    "startMinute": 0,
    "endHour": 7,
    "endMinute": 0,
    "activeNow": false
  }
}
```

---

## Error Responses

### Unauthorized (Missing or Invalid API Key)
```json
{
  "success": false,
  "message": "Unauthorized: Invalid or missing API key",
  "error": "INVALID_API_KEY"
}
```

### Launch Failed
```json
{
  "success": false,
  "message": "Failed to launch app",
  "error": "LAUNCH_FAILED"
}
```

### Invalid Request
```json
{
  "success": false,
  "message": "Invalid request format",
  "error": "INVALID_REQUEST"
}
```

---

## Home Assistant Integration

### Example YAML Configuration

```yaml
# Home Assistant configuration.yaml

rest_command:
  launch_app:
    url: "http://{{ device_ip }}:3001/api/launch"
    method: POST
    headers:
      X-API-Key: !secret app_launcher_api_key
      Content-Type: "application/json"
    payload: '{"packageName": "{{ package_name }}"}'

  quiet_hours_set_enabled:
    url: "http://{{ device_ip }}:3001/api/quiet-hours/enabled"
    method: POST
    headers:
      X-API-Key: !secret app_launcher_api_key
      Content-Type: "application/json"
    payload: '{"enabled": {{ enabled | lower }}}'

  quiet_hours_update:
    url: "http://{{ device_ip }}:3001/api/quiet-hours"
    method: POST
    headers:
      X-API-Key: !secret app_launcher_api_key
      Content-Type: "application/json"
    payload: '{"enabled": {{ enabled | lower }}, "startHour": {{ start_hour }}, "startMinute": {{ start_minute }}, "endHour": {{ end_hour }}, "endMinute": {{ end_minute }}}'

automation:
  - alias: "Open Immich On Living Room Device"
    trigger:
      platform: webhook
      webhook_id: launch_frame_webhook
    action:
      service: rest_command.launch_app
      data:
        device_ip: "192.168.1.100"
        package_name: "com.immichframe.immichframe"

  - alias: "Enable Quiet Hours Every Night"
    trigger:
      platform: time
      at: "21:55:00"
    action:
      service: rest_command.quiet_hours_update
      data:
        device_ip: "192.168.1.100"
        enabled: true
        start_hour: 22
        start_minute: 0
        end_hour: 7
        end_minute: 0
```

### Example Automation

```yaml
# Trigger Unifi Protect when motion is detected
automation:
  - alias: "Show Unifi Protected Camera on Motion"
    trigger:
      - platform: state
        entity_id: binary_sensor.backyard_motion
        to: 'on'
    action:
      - service: rest_command.launch_unifi_protect
```

---

## Common Use Cases

### Open Immich Frame
```bash
curl -X POST http://192.168.1.100:3001/api/launch \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{"packageName": "com.immichframe.immichframe"}'
```

### Enable Quiet Hours
```bash
curl -X POST http://192.168.1.100:3001/api/quiet-hours/enabled \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'
```

### Set Quiet Hours Schedule
```bash
curl -X POST http://192.168.1.100:3001/api/quiet-hours \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "startHour": 22,
    "startMinute": 0,
    "endHour": 7,
    "endMinute": 0
  }'
```

### Open Unifi Protect to Front Door Camera
Replace `CAMERA_ID` with your actual camera ID from Unifi:

```bash
curl -X POST http://192.168.1.100:3001/api/launch \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{
    "packageName": "com.ubnt.android.protect",
    "deepLink": "unifi-protect://camera/CAMERA_ID"
  }'
```

---

## Finding Unifi Camera IDs

To find your Unifi Protect camera IDs:
1. Open Unifi Protect app on your Android device
2. Tap on a camera
3. Check the camera details or URL pattern
4. Try viewing the app's debug logs or network requests

Alternatively, contact Ubiquiti support or check the Unifi Protect API documentation.

---

## Security Recommendations

1. **Change Default API Key**
   - The default key is `app-launcher-default-key`
   - Change this in the Android app settings (SharedPreferences)
   - Use a strong, random key for production

2. **Network Security**
   - Only allow connections from your local network (firewall rules)
   - Use a VPN if accessing remotely
   - Consider using HTTPS (requires certificate setup)

3. **Home Assistant Security**
   - Keep your Home Assistant instance secure
   - Use strong authentication
   - Don't expose the app launcher API directly to the internet

---

## Troubleshooting

### API Not Responding
- Check if the Android app is running
- Verify the device IP address and port (3001)
- Check firewall rules allowing port 3001

### "Unauthorized" Error
- Verify the API key in the header
- Check the API key in the Android app

### "App Not Installed" Error
- Confirm the app is installed on the device
- Check the package name is correct
- Verify in `/api/apps` endpoint that the app appears in `data.apps`

### Deep Link Not Working
- Verify the app supports the deep link scheme
- Check the deep link format matches the app's expected format
- Try launching the app without deep link first

---

## Future Enhancements

- [ ] Find Unifi Protect deep link format for camera views
- [ ] Add more supported apps
- [ ] HTTPS/TLS support
- [ ] Advanced authentication (OAuth2)
- [ ] App launch history logging
- [ ] Rate limiting
- [ ] Custom app configurations
