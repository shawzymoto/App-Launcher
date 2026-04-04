# Home Assistant Integration Guide

Complete guide for integrating the App Launcher REST API with Home Assistant.

## Prerequisites

- App Launcher running on an Android device
- Home Assistant instance on the same local network
- Device IP address (e.g., 192.168.1.100)
- App Launcher API key (default: `app-launcher-default-key`)

## Basic Setup

### 1. Add REST Commands

Add a **single generic command** to your Home Assistant `configuration.yaml`. The device IP and app are passed as variables each time the command is called, so one command works across all devices and apps:

```yaml
# configuration.yaml
rest_command:
  launch_app:
    url: "http://{{ device_ip }}:3001/api/launch"
    method: POST
    headers:
      X-API-Key: !secret app_launcher_api_key
      Content-Type: "application/json"
    payload: '{"packageName": "{{ package_name }}"}'

  launch_app_deeplink:
    url: "http://{{ device_ip }}:3001/api/launch"
    method: POST
    headers:
      X-API-Key: !secret app_launcher_api_key
      Content-Type: "application/json"
    payload: '{"packageName": "{{ package_name }}", "deepLink": "{{ deep_link "}}"}'
```

```yaml
# secrets.yaml
app_launcher_api_key: "app-launcher-default-key"  # Change this!
```

Call it like this (from automations, scripts, or Developer Tools):

```yaml
service: rest_command.launch_app
data:
  device_ip: "192.168.1.100"
  package_name: "com.immichframe.immichframe"
```

### 2. Reload the Configuration

After saving `configuration.yaml`, HA does **not** pick up changes automatically. You must reload:

1. Go to **Developer Tools > YAML**
2. Click **Reload All YAML** (or scroll down and click **Reload** next to the specific domain — "REST Command" or "Scripts")

If you don't see those buttons, a full restart also works: **Settings > System > Restart**.

### 3. Test the Connection

`rest_command` entries appear in Developer Tools as services. In newer versions of HA (2024.2+) **Services** was renamed to **Actions**:

1. Go to **Developer Tools > Actions** (or **Services** on older HA)
2. In the Action/Service field type `rest_command.launch_app`
3. Switch to **YAML mode** (toggle in the top-right of the card) and enter:
```yaml
service: rest_command.launch_app
data:
  device_ip: "192.168.1.100"
  package_name: "com.immichframe.immichframe"
```
4. Click **Perform Action** (or **Call Service**) — you should see the app launch on your device

> **Note:** Scripts defined in `configuration.yaml` show up as `script.<script_name>` in the same Actions/Services panel, but they only appear after reloading. If a script is missing, double-check the YAML indentation — `script:` must be at the root level (no leading spaces).

## Advanced Examples

### Launch Unifi Protect with Specific Camera

First, find your camera ID from the Unifi Protect app or API. Then use `launch_app_deeplink` — no extra command definition needed:

```yaml
service: rest_command.launch_app_deeplink
data:
  device_ip: "192.168.1.100"
  package_name: "com.ubnt.android.protect"
  deep_link: "unifi-protect://camera/5f1a2b3c4d"  # Replace with actual camera ID
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
      - service: rest_command.launch_app
        data:
          device_ip: "192.168.1.100"
          package_name: "com.ubnt.android.protect"
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
      - service: rest_command.launch_app
        data:
          device_ip: "192.168.1.100"
          package_name: "com.immichframe.immichframe"
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
      - service: rest_command.launch_app
        data:
          device_ip: "192.168.1.100"
          package_name: "com.ubnt.android.protect"
      - delay:
          seconds: 1
```

## Script Examples

### Manual Launch Script

Scripts are a great way to give each device+app combination a friendly name without duplicating `rest_command` definitions:

```yaml
script:
  launch_unifi_living_room:
    description: "Launch Unifi Protect on the living room tablet"
    sequence:
      - service: rest_command.launch_app
        data:
          device_ip: "192.168.1.100"
          package_name: "com.ubnt.android.protect"

  launch_immich_bedroom:
    description: "Launch Immich photo frame on the bedroom tablet"
    sequence:
      - service: rest_command.launch_app
        data:
          device_ip: "192.168.1.101"
          package_name: "com.immichframe.immichframe"

  launch_unifi_camera:
    description: "Launch Unifi Protect and show a specific camera"
    fields:
      device_ip:
        description: "IP address of the target tablet"
        example: "192.168.1.100"
      camera_id:
        description: "Camera ID to show"
        example: "5f1a2b3c4d"
      camera_name:
        description: "Camera name for logging"
        example: "Front Door"
    sequence:
      - service: rest_command.launch_app_deeplink
        data:
          device_ip: "{{ device_ip }}"
          package_name: "com.ubnt.android.protect"
          deep_link: "unifi-protect://camera/{{ camera_id }}"
      - service: notify.persistent_notification
        data:
          title: "Camera Launched"
          message: "Opened {{ camera_name }} camera in Unifi Protect"
```

Call from automations:
```yaml
action:
  - service: script.launch_unifi_camera
    data:
      device_ip: "192.168.1.100"
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
name: "Launch Unifi Protect"
tap_action:
  action: call-service
  service: rest_command.launch_app
  service_data:
    device_ip: "192.168.1.100"
    package_name: "com.ubnt.android.protect"
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
  service: rest_command.launch_app
  service_data:
    device_ip: "192.168.1.100"
    package_name: "com.ubnt.android.protect"
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
curl -X POST http://192.168.1.100:3001/api/launch \
  -H "X-API-Key: app-launcher-default-key" \
  -H "Content-Type: application/json" \
  -d '{"packageName": "app.immich"}'

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

The generic command already uses `!secret` — just set the key in `secrets.yaml`:

```yaml
# secrets.yaml
app_launcher_api_key: "your-secure-api-key-here"
```

If you run different API keys on different devices, add one entry per device:

```yaml
# secrets.yaml
app_launcher_api_key_living_room: "key-for-device-1"
app_launcher_api_key_bedroom: "key-for-device-2"
```

Then define a separate `rest_command` per key, or pass the key as a variable (note: `!secret` cannot be templated, so per-key commands are the easiest approach in that case).

## Multiple Devices

Because `device_ip` is a variable, you need **no extra `rest_command` entries** for additional devices — just pass a different IP:

```yaml
action:
  # Launch on every tablet at once
  - service: rest_command.launch_app
    data:
      device_ip: "192.168.1.100"
      package_name: "com.ubnt.android.protect"
  - service: rest_command.launch_app
    data:
      device_ip: "192.168.1.101"
      package_name: "com.ubnt.android.protect"
  - service: rest_command.launch_app
    data:
      device_ip: "192.168.1.102"
      package_name: "com.ubnt.android.protect"
```

For cleaner automations, define device IPs once as `input_text` helpers (Settings > Helpers) and reference them:

```yaml
action:
  - service: rest_command.launch_app
    data:
      device_ip: "{{ states('input_text.tablet_living_room_ip') }}"
      package_name: "com.ubnt.android.protect"
  - service: rest_command.launch_app
    data:
      device_ip: "{{ states('input_text.tablet_bedroom_ip') }}"
      package_name: "com.ubnt.android.protect"
```

This lets you update IPs from the HA UI if a device gets a new address, with no `configuration.yaml` changes.

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
