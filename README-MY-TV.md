# TCL 43S434 (Android TV 11 / AR05) Cleanup Guide

This TV has been debloated and upgraded with a custom Apple TV 4K-style interface, live weather beside the clock, and a 2-minute rotating wallpaper system.

---

## What Was Done

1. **23 Bloatware Packages Disabled:**
   - TCL BI telemetry, crash reporting, and tracking services.
   - TCL recommendation waterfall, promo popups, messagebox, and retail demo stickers.
   - Duplicate voice assistant and Alexa services (Google Assistant/Voice Typing was kept active).
   - Stock Google TV ad-filled launcher (`tvlauncher`) disabled and replaced.
   - Stock Google Backdrop screensaver (`com.google.android.backdrop`) disabled and replaced with CleanYourTV ClockDream.
2. **Apple TV 4K Style Launcher Installed (`com.gathin.tvhome`):**
   - Frosted glass dock with your 5 preferred apps: **Sling, Nuvio, YouTube, Netflix, Prime Video**.
   - App drawer below with Stremio, Downloader, Google Play Store, and Weather.
   - Ambient top-shelf glow matching the focused app.
   - Icon-only glass pills for Inputs, Wi-Fi, and Settings.
3. **Live Weather Directly Beside the Clock:**
   - Permanent weather reading (`· 49°F ⛅`) directly next to the clock on the **Home screen**.
   - Synchronized large weather readout on the **Lockscreen / Clock Screensaver**.
   - Auto-updates every 30 minutes in the background from Open-Meteo with zero advertising or tracking.
4. **2-Minute Rotating Wallpaper Collection:**
   - 18 high-resolution wallpapers combining curated Unsplash landscapes (Alps, Atacama, Emerald Lake) and random crisp photography.
   - Rotates automatically every **2 minutes** on Home and is fully synchronized with the lockscreen screensaver.

---

## How to Customize

### Change Dock Apps or Order
Edit `cleanyourtv\themes\custom-mix\config.json`:
```json
"dock_apps": [
  "com.sling",
  "com.nuvio.tv",
  "com.google.android.youtube.tv",
  "com.netflix.ninja",
  "com.amazon.amazonvideo.livingroom"
]
```
Then push the update to your TV:
```powershell
powershell -File .\tools\theme.ps1 -TvHost 192.168.8.243 .\themes\custom-mix
```

### Add or Change Wallpapers
Drop any `.jpg` or `.png` images into `cleanyourtv\themes\custom-mix\wallpapers\`.
Then run the theme push command above.

### Re-Enable Any App
To bring back any disabled app at any time:
```powershell
.\tools\venv\Scripts\python.exe .\tools\tv.py "pm enable <package_name>"
```

---

## One-Click Scripts

* **Full Restore Back to Stock:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\restore-my-tv.ps1
  ```
  *(Re-enables all 22 debloated packages, puts the stock Google TV launcher back as default, and restarts the TV).*

* **Rebuild & Reinstall Launcher:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\build-my-tv.ps1
  ```

---

## Sling TV Optimization & AdGuard Network Fix

If Sling TV is sluggish across your TVs, the investigation revealed the exact root cause:

### 1. Flint 2 / AdGuard Home DNS Trap (Why it happens on ALL your TVs)
Sling TV integrates three intensive telemetry/analytics engines:
- **Dynatrace Mobile RUM** (`bf90969srk.bf.dynatrace.com`)
- **Conviva Streaming Telemetry** (`cws.conviva.com`)
- **MoveTV Analytics** (`tics.movetv.com`)

When AdGuard Home blocks these domains using the default **127.0.0.1** return address:
1. Android attempts a local SSL handshake to `127.0.0.1:443`, which fails with `ECONNREFUSED`.
2. Conviva and Dynatrace treat this connection refusal to an IP as a transient network error and enter an aggressive retry loop.
3. This burns **~89% of a CPU core** and triggers **850ms Garbage Collection freezes every 2 seconds**, causing massive 1-2 second navigation delays on remote clicks.

**How to fix it in AdGuard Home on your Flint 2:**
1. Open your AdGuard Home web dashboard (`http://192.168.8.1:3000` or your router IP).
2. Go to **Filters** → **Custom filtering rules**, and add allow rules for Sling:
   ```text
   @@||cws.conviva.com^
   @@||conviva.com^
   @@||dynatrace.com^
   @@||movetv.com^
   ```
   *(Alternatively, under **Settings** → **General Settings** → **Blocking mode**, switch from `Default / 127.0.0.1` to **NXDOMAIN**).*
3. Once these endpoints either respond with NXDOMAIN or return normal lightweight responses, the Conviva and Dynatrace threads go to sleep (0% CPU), eliminating the 850ms stutter!

### 2. TV-Side Optimizations Applied
- **Native AOT Compilation:** Sling was compiled into full Ahead-Of-Time native ARM machine code (`cmd package compile -m speed -f com.sling`), cutting down JIT compilation overhead.
- **Single-App Enforcement:** `standard_background_process_limit` was set to `1`, ensuring background apps are evicted so Sling gets the maximum available RAM (350+ MB).
- **Background Voice Assistant Throttled:** Restricted `com.google.android.katniss:interactor` from consuming 30-50% CPU in the background, while keeping the remote's Google Assistant mic button active.

---

## Security Recommendation
When you are done testing, turn off **USB debugging** and **Wireless debugging** in your TV's **Developer Options** to secure your TV from unauthorized local network access.
