# NotifyGlance 📲

**Hands-free, read-only notification overlay for Android.**

NotifyGlance captures your device notifications and displays them as auto-cycling overlay cards — no touching required. Ideal for use while driving, cooking, exercising, or any hands-free scenario.

---

## What It Does

| Feature | Detail |
|---|---|
| **Read-only** | No reply, dismiss, or mark-as-read. Pure display. |
| **Overlay cards** | Floating cards appear over any app or lock screen |
| **Auto-cycle** | Each card displays for N seconds, then advances automatically |
| **Local storage** | All notifications stored in a local Room database |
| **Timed sessions** | Replay notifications every hour or every X minutes |
| **Quiet hours** | Suppress overlays during specified time window |
| **App filter** | Choose exactly which apps to include |

---

## Setup

### 1. Build & Install

**Option A – Android Studio**
1. Open the `NotifyGlance/` folder in Android Studio.
2. Click **Run ▶** on a physical device (emulator cannot test overlays on lock screen).

**Option B – GitHub Codespaces**
```bash
# Install Java & Android SDK (see full guide in repo wiki)
./gradlew assembleDebug
# Download: app/build/outputs/apk/debug/app-debug.apk
# Transfer to phone and install
```

---

## Permissions Walkthrough

You need **two special permissions** that cannot be granted automatically.

### 1. Notification Access
> *Settings → Apps → Special app access → Notification access → NotifyGlance → Enable*

Or tap **"Grant Notification Access"** in the app's main screen.

⚠️ **Without this, no notifications are captured.**

📸 *[Screenshot placeholder: Notification Listener Settings screen]*

### 2. Overlay Permission (Draw over other apps)
> *Settings → Apps → NotifyGlance → Display over other apps → Allow*

Or tap **"Grant Overlay Permission"** in the app's main screen.

⚠️ **Without this, no overlay cards appear.**

📸 *[Screenshot placeholder: Overlay permission screen]*

### 3. Battery Optimization (Recommended)
Exclude NotifyGlance from battery optimization to prevent the service from being killed:
> *Settings → Battery → Battery optimization → All apps → NotifyGlance → Don't optimize*

---

## Configuring Timed Sessions

Navigate to **Settings → When to Display → Timed Sessions**.

| Option | Behavior |
|---|---|
| **Off** | No periodic triggers (only triggered by new notifications if enabled) |
| **Every Hour** | WorkManager fires the overlay session every 60 minutes |
| **Every X Minutes** | Enter a value between 5–60 minutes; WorkManager schedules accordingly |

Changes take effect immediately. WorkManager survives reboots automatically.

---

## Configuring Quiet Hours

Navigate to **Settings → Quiet Hours**.

- Set **Quiet Hours Start** and **Quiet Hours End** in `HH:mm` format (24-hour clock).
- Example: Start `22:00`, End `07:00` → no overlays from 10pm to 7am.
- Spans midnight automatically.
- **Capture continues** during quiet hours — notifications are stored but not displayed until quiet hours end.

---

## Privacy on Lock Screen

By default, overlay cards appear on the lock screen and turn the screen on.

This is controlled by `AndroidManifest.xml` flags:
```xml
android:showWhenLocked="true"
android:turnScreenOn="true"
```

And the overlay window flags:
```java
WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
```

**To disable lock-screen display**, remove these flags from `OverlayService.java` and the `OverlayActivity` manifest entry.

> Note: On Android 12+, some OEMs require the full-screen intent permission for lock screen display. The `USE_FULL_SCREEN_INTENT` permission is declared in the manifest.

---

## Known OEM Caveats

### Samsung (One UI)
- **Device Care / Auto-restart**: Disable auto-optimization for NotifyGlance in *Settings → Device Care → Battery → App power management*.
- **AOD (Always-On Display)**: The overlay will not appear over AOD by default. Wake the screen first.
- **Secure Folder notifications**: Cannot be captured (by design).

### Xiaomi / MIUI
- **Autostart**: Enable autostart for NotifyGlance in *Settings → Manage apps → NotifyGlance → Autostart*.
- **Battery Saver**: Set to "No restrictions" to keep the overlay service alive.
- **Notification shade lock**: Disable "Lock notification shade" in MIUI developer settings.

### Huawei (EMUI)
- **App Launch**: Enable "Manage manually" and toggle all background modes in *Settings → Apps → App Launch*.

### OnePlus (OxygenOS)
- **Battery Optimization**: Set NotifyGlance to "Allow background activity".

### Google Pixel
- Works without additional configuration on stock Android.

---

## Test Plan

### Unlocked Screen Test
1. Enable master toggle in Settings.
2. Grant both permissions.
3. Send a test notification from another app (e.g., Telegram message).
4. Verify overlay card appears within ~2 seconds.
5. Verify card auto-advances after configured time.
6. Verify overlay disappears when queue is empty.

### Lock Screen Test
1. Lock the device.
2. Send a notification from another app.
3. Verify overlay card appears on lock screen without unlocking.

### Notification Flood Test
1. Receive 20+ notifications rapidly.
2. Verify app only shows `maxCards` per session.
3. Verify no crash or ANR.

### DND (Do Not Disturb) Test
1. Enable DND on the device.
2. Send notifications.
3. NotifyGlance captures ALL notifications regardless of DND (the NLS receives them even if silenced).

### OTP / Sensitive Notification Test
1. Request an SMS OTP.
2. Verify the OTP appears in the overlay as plain text (expected behavior — user should configure App Filter to exclude SMS if desired).
3. To hide SMS: go to *Settings → Filters → Allowed Apps* and deselect your SMS app.

### Quiet Hours Test
1. Set quiet start = current time + 1 min, quiet end = current time + 2 min.
2. Wait for quiet period.
3. Send a notification — verify NO overlay appears.
4. Wait for quiet period to end.
5. Trigger timed session — verify overlay appears.

### Cycle Repeat Test
1. View all notifications (queue drains).
2. Wait for next timed session trigger.
3. Verify all notifications replay from the beginning.

---

## Architecture Overview

```
GlanceNotificationListenerService  →  captures raw StatusBarNotification
         ↓
    NotificationEntity (Room DB)
         ↓
    OverlayService (Foreground)   →  WindowManager overlay cards
         ↑
    TimedSessionWorker (WorkManager)  →  periodic triggers
    BootReceiver                      →  reschedules WorkManager on boot
```

---

## License

MIT — free to use and modify.
