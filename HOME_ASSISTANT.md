# Home Assistant Integration Guide

Complete guide for integrating the App Launcher REST API with Home Assistant.

## Prerequisites

- App Launcher running on an Android device
- Home Assistant instance on the same local network
- Device IP address (e.g., 192.168.1.100)
- App Launcher API key (default: `app-launcher-default-key`)

## Basic Setup

### 1. Add REST Commands

Add the following to your Home Assistant `configuration.yaml`:

```yaml
rest_command:
  launch_immich_frame:
    url: "http://192.168.1.100:3001/api/launch/app.immich"
    method: POST
    headers:
      X-API-Key: "app-launcher-default-key"

  launch_unifi_protect:
    url: "http://192.168.1.100:3001/api/launch/com.ubnt.android.protect"
    method: POST
    headers:
      X-API-Key: "app-launcher-default-key"
```

Replace `192.168.1.100` with your device's IP address.

### 2. Test the Connection

Use the Developer Tools - Services in Home Assistant:
1. Go to Developer Tools > Services
2. Select `rest_command.launch_immich_frame`
3. Click "Call Service"
4. You should see the app launch on your device

## Advanced Examples

### Launch Unifi Protect with Specific Camera

First, find your camera ID from the Unifi Protect app or API.

```yaml
rest_command:
  launch_unifi_camera:
    url: "http://192.168.1.100:3001/api/launch"
    method: POST
    headers:
      X-API-Key: "app-launcher-default-key"
      Content-Type: "application/json"
    payload: >-
      {
        "packageName": "com.ubnt.android.protect",
        "deepLink": "unifi-protect://camera/{{ camera_id }}"
      }
```

Use in automation:
```yaml
service: rest_command.launch_unifi_camera
data:
  camera_id: "5f1a2b3c4d"  # Replace with actual camera ID
```

### Motion Detection Automation

Launch Unifi Protect when motion is detected:

```yaml
automation:
  - alias: "Show Unifi on Motion Detection"
    description: "Launch Unifi Protect when motion is detected"
    trigger:
      - platform: state
        entity_id: binary_sensor.backyard_motion
        to: 'on'
    condition: []
    action:
      - service: rest_command.launch_unifi_protect
      - delay:
          seconds: 2
    mode: single
```

### Time-Based Automation

Launch Immich Frame at a specific time:

```yaml
automation:
  - alias: "Show Frame in Morning"
    trigger:
      platform: time
      at: "07:00:00"
    action:
      - service: rest_command.launch_immich_frame
```

### Door Open Detection

Show security camera when front door opens:

```yaml
automation:
  - alias: "Show Camera on Door Open"
    trigger:
      - platform: state
        entity_id: binary_sensor.front_door_contact
        to: 'on'
    action:
      - service: rest_command.launch_unifi_protect
      - delay:
          seconds: 1
```

## Script Examples

### Manual Launch Script

Create a script to launch apps from Home Assistant UI:

```yaml
script:
  launch_unifi:
    description: "Launch Unifi Protect security app"
    sequence:
      - service: rest_command.launch_unifi_protect
        data: {}

  launch_immich:
    description: "Launch Immich photo frame"
    sequence:
      - service: rest_command.launch_immich_frame
        data: {}

  launch_unifi_camera:
    description: "Launch Unifi Protect and show specific camera"
    fields:
      camera_id:
        description: "Camera ID to show"
        example: "5f1a2b3c4d"
      camera_name:
        description: "Camera name for logging"
        example: "Front Door"
    sequence:
      - service: rest_command.launch_unifi_camera
        data:
          camera_id: "{{ camera_id }}"
      - service: notify.persistent_notification
        data:
          title: "Camera Launched"
          message: "Opened {{ camera_name }} camera in Unifi Protect"
```

Then call from automations:
```yaml
action:
  - service: script.launch_unifi_camera
    data:
      camera_id: "5f1a2b3c4d"
      camera_name: "Front Door"
```

## Sensor to Check API Availability

Monitor if the API is available:

```yaml
rest:
  - resource: http://192.168.1.100:3001/health
    scan_interval: 60
    sensor:
      - name: "App Launcher API Status"
        json_attributes:
          - port
          - version
        value_template: "{{ value_json.success }}"
        unique_id: app_launcher_api_status
```

Display in UI:
```yaml
- type: gauge
  entity: sensor.app_launcher_api_status
  min: 0
  max: 1
```

## Custom Card Example (Lovelace)

Create a button card for app launching:

```yaml
type: custom:button-card
entity: switch.app_launcher_switch  # Create a dummy switch/template
name: "Launch Unifi Protect"
tap_action:
  action: call-service
  service: rest_command.launch_unifi_protect
  data: {}
state_color: true
color_type: icon
icon: mdi:shield-camera
```

Or use native button card:

```yaml
type: button
name: Launch Unifi
icon: mdi:shield-camera
tap_action:
  action: call-service
  service: rest_command.launch_unifi_protect
```

## Troubleshooting

### Connection Refused
```
Error: Connection refused
```
- Check device IP address is correct
- Verify App Launcher app is running
- Ensure port 3001 is not blocked by firewall

### Unauthorized Error
```
"success": false,
"error": "INVALID_API_KEY"
```
- Verify API key is correct in the rest_command
- Check for typos in the X-API-Key header
- Default key: `app-launcher-default-key`

### App Not Launching
- Verify app is installed on device
- Check package name is correct with `/api/apps` endpoint
- Some apps may not support deep links

### Test with curl

Before adding to Home Assistant, test the REST API:

```bash
# Check API is running
curl http://192.168.1.100:3001/health

# List available apps
curl http://192.168.1.100:3001/api/apps \
  -H "X-API-Key: app-launcher-default-key"

# Launch app
curl -X POST http://192.168.1.100:3001/api/launch/app.immich \
  -H "X-API-Key: app-launcher-default-key"

# Launch with deep link
curl -X POST http://192.168.1.100:3001/api/launch \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{
    "packageName": "com.ubnt.android.protect",
    "deepLink": "unifi-protect://camera/CAMERA_ID"
  }'
```

### Check Home Assistant Logs

In Home Assistant, check the logs:
```
Configuration > Logs
```

Look for errors related to `rest_command`.

## Security Considerations

### Local Network Only
- API listens on port 3001
- Only accessible from your local network
- Recommended: Use firewall rules to restrict access

### API Key Management
1. Change default API key from `app-launcher-default-key`
2. Use a strong, unique key (e.g., 32-character random string)
3. Store securely in `secrets.yaml` (Home Assistant best practice)

### Using secrets.yaml

Instead of hardcoding the API key:

```yaml
# configuration.yaml
rest_command:
  launch_unifi_protect:
    url: "http://192.168.1.100:3001/api/launch/com.ubnt.android.protect"
    method: POST
    headers:
      X-API-Key: !secret app_launcher_api_key
```

```yaml
# secrets.yaml
app_launcher_api_key: "your-secure-api-key-here"
```

## Multiple Devices

If you have multiple Android devices with App Launcher:

```yaml
rest_command:
  launch_unifi_bedroom:
    url: "http://192.168.1.101:3001/api/launch/com.ubnt.android.protect"
    method: POST
    headers:
      X-API-Key: !secret app_launcher_api_key

  launch_unifi_living_room:
    url: "http://192.168.1.102:3001/api/launch/com.ubnt.android.protect"
    method: POST
    headers:
      X-API-Key: !secret app_launcher_api_key
```

Then use in automations:
```yaml
action:
  - service: rest_command.launch_unifi_bedroom
  - service: rest_command.launch_unifi_living_room
```

## Performance Tips

1. **Add delays** if launching multiple apps:
```yaml
action:
  - service: rest_command.launch_unifi_protect
  - delay:
      seconds: 2
  - service: rest_command.launch_immich_frame
```

2. **Use conditions** to prevent unnecessary calls:
```yaml
condition:
  - condition: state
    entity_id: person.user
    state: home

action:
  - service: rest_command.launch_unifi_protect
```

## API Documentation

For complete API documentation, see [API_DOCUMENTATION.md](API_DOCUMENTATION.md) in the project root.

### Useful Endpoints

- `GET /health` - Check API is running
- `GET /api/apps` - List installed apps
- `GET /api/config` - Get server configuration
- `POST /api/launch/{packageName}` - Launch app
- `POST /api/launch` - Launch with options

## Community Resources

- [Home Assistant Documentation](https://www.home-assistant.io/)
- [REST Command Integration](https://www.home-assistant.io/integrations/rest_command/)
- [Automation Guide](https://www.home-assistant.io/docs/automation/)
- [Lovelace Custom Cards](https://www.home-assistant.io/lovelace/custom-card/)

## Support

For issues or questions:
1. Check the [Troubleshooting](#troubleshooting) section
2. Review [API_DOCUMENTATION.md](API_DOCUMENTATION.md) for API details
3. Test with curl before adding to Home Assistant
4. Check Home Assistant logs for errors
