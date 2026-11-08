# BPL 55U-C5510 notes

This profile is for the BPL 55U-C5510 sold by Changhong as an **AI PONT** device.
It was checked on the owner's unit over the cleanup session and rechecked after the
launcher changes. The list beside this note disables **11 packages**; it never uninstalls
anything.

| Field | Observed value |
|---|---|
| Android / API | 11 / 30 |
| Manufacturer / model | Changhong / AI PONT |
| Product / device | DVB96BL / central |
| Board | MediaTek m7632, 32-bit `armeabi-v7a` |
| Firmware | `RTM5.220609.235` |
| Display | 1920 × 1080, 320 dpi; 50/60 Hz modes |
| Network during testing | Wi-Fi on `wlan0`; no USB peripheral or USB Ethernet adapter |
| Live TV | `com.mediatek.wwtv.tvcenter` |
| Home after handover | `com.gathin.tvhome/.MainActivity` |

## What was disabled

The tested profile disables the Homwee store/help and two unused media utilities,
Google Play Games and Movies/TV, the Google ambient screensaver, Google Assistant,
the two Changhong screen-sharing receivers, and Google TV's setup wizard after the
saved Wi-Fi network was checked. The stock launcher is disabled by
`launcher/build-and-install.sh` after the new Home has answered successfully.

The owner kept streaming apps, Chromecast built-in, Play Store, Play Services, the
MediaTek TV/input stack, Bluetooth and remote pairing, and both firmware/app update
packages (`com.homwee.dmt.upgrade` and `com.homwee.upgrade`). The factory package
(`com.homwee.factory`) and remote-key service (`com.homwee.synckey`) stay enabled and
were never launched. `com.homwee.lightsensorservice` also stays enabled because it is
part of the TV's ambient-light hardware path.

The unit already had Netflix and YouTube Music disabled before this profile was made;
YouTube was also disabled on the unit later, but none of those three packages is sent
by this list. Re-enable them separately if they are wanted on another installation.

The package inventory did not find `com.cltv.fast`, `com.homwee.aipont` or
`tv.anoki.acr.anokiacroptin` on this firmware. A separate `com.ionitech.airscreen`
package is installed, but it was not disabled by this BPL profile; the Changhong
receiver disabled here is `com.homwee.mmp.screenshare`.

## Launcher configuration

`config.json` carries the tested five-slot dock—Stremio, Prime Video, Hotstar, YouTube
and ZEE5—plus the memory display, glass dock, Settings key, Assistant notice, Quick
Access key, app-key mappings and accessibility-service recovery. The launcher source's
TV-specific XML values also set:

```xml
<string name="arc_audio_input">HW5</string>
<string name="arc_audio_label">Soundbar (ARC)</string>
```

Those values make the first input read `HDMI 1 · Soundbar (ARC)`.

The Assistant package is `com.google.android.katniss`. The launcher maps both the
standard Assistant key and this remote's keycode 232 to a bilingual disabled notice.
Keycode 82 opens Quick Access; keycode 83 opens Settings; keycodes 247, 265 and 249
open SmartTube, Stremio and Hotstar respectively. Zee5 has no remote-key mapping.

## Home, Wi-Fi and inputs

Google TV's `com.google.android.tungsten.setupwraith` was disabled only after checking
that the saved Wi-Fi profile belonged to the system Settings service. On another unit,
check ownership first with:

```text
dumpsys wifi | grep -o 'cname=[^ ]*'
```

If setupwraith owns the saved network, forget it and re-join it in Settings before
disabling setupwraith. The TV uses Wi-Fi, not USB Ethernet; `host_connected=false` was
not used as the only USB test.

The MediaTek HDMI inputs are:

| Port | Input ID |
|---|---|
| HDMI 1 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW5` |
| HDMI 2 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW6` |
| HDMI 3 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW7` |
| HDMI 4 | `com.mediatek.tvinput/.hdmi.HDMIInputService/HW8` |

HDMI 1 reports an ARC-established CEC audio system, so the launcher labels it
`HDMI 1 · Soundbar (ARC)`. At the time of the check, HDMI 2–4 exposed no CEC device
name or type; they remain ordinary HDMI rows until a connected device advertises one.
HDMI and protected Live TV surfaces can produce empty screenshots even when switching
works; verify those with the resumed activity and input state.

The Wi-Fi panel excludes the currently connected network from nearby results and makes
one bounded internet-throughput check on Home startup, cached for 30 minutes. AdGuard
Private DNS was tested on this firmware but is optional and deliberately not configured
by this profile; it does not block YouTube ads and bypasses router-level DNS rules.

## Screensaver and restore

To restore the packages disabled by this profile and re-enable the stock launcher:

```text
TV_HOST=<tv ip> tools/restore.sh examples/bpl-55u-c5510/debloat.txt com.google.android.apps.tv.launcherx
```

Before disabling `com.google.android.apps.tv.dreamx`, select the launcher's Clock
screensaver:

```text
settings put secure screensaver_components com.gathin.tvhome/.ClockDream
settings put secure screensaver_default_component com.gathin.tvhome/.ClockDream
```

The package restore command re-enables every package in the list and the stock launcher;
it does not restore the screensaver setting or the launcher overlay choices. Restore the
stock screensaver manually when needed:

```text
settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
settings put secure screensaver_default_component com.google.android.apps.tv.dreamx/.service.Backdrop
```

The launcher can be rebuilt and installed with the normal repository script:

```text
TV_HOST=<tv ip> launcher/build-and-install.sh
```

Keep debugging enabled only while working. Once finished, turn off USB/network or
Wireless debugging and revoke its authorisations on the TV.
