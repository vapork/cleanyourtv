# Low-End Android TV Optimization Guide

> **Tested and verified on:** TCL 43S434 (Realtek RTD2851, 2 GB RAM, Android TV 11 AR05 / V8-R851T02-LF1V728).  
> **Applies to:** Any 1 GB – 2 GB Android TV or Google TV set (TCL, Xiaomi, Hisense, Sony, Philips, Onn, Fire TV) powered by Realtek (RTD2851/RTD287x), Amlogic (S905X/W), or MediaTek budget SoCs.

---

## 1. The Hardware Reality: Why Budget TVs Lag

Many users assume a 2 GB Android TV has 2 GB of memory available for applications. In reality:

```
[ Total Physical RAM: 2,048 MB ]
 ├── Hardware CMA (Contiguous Memory Allocator):  ~608 MB (Reserved for 4K video decoders, HDMI inputs, SurfaceFlinger)
 ├── GPU Shared VRAM & Widevine L1 (OP-TEE):       ~350 MB (Mali GPU graphics + DRM crypto)
 ├── Core OS & Drivers (SystemServer, Bluetooth):  ~300 MB
 ├── Custom Clean Launcher (tvhome):                ~54 MB (vs. ~180 MB for stock Google TV launcher)
 └── Available App RAM Headroom:                  ~520 - 550 MB (Maximum heap space for streaming apps)
```

When heavy streaming apps like **Sling TV, Hulu, or YouTube TV** launch, they consume **380 MB to 510 MB** of RAM just decoding video and caching UI views. 

If stock manufacturer bloatware, background recommendation scrapers, and always-listening microphone services run concurrently, the available memory drops below 150 MB. This forces the Linux kernel into **Low Memory Killer (`lmkd`) thrashing**, causing:
- Frame times ballooning to 60–120 ms (sluggish 8–15 FPS navigation).
- 850 ms Garbage Collection pauses freezing the remote control.
- Random app restarts when switching between apps.

---

## 2. Before vs. After Optimization Benchmarks

Measured on device using Android's hardware rendering pipeline monitor (`dumpsys gfxinfo com.sling`):

| Metric | Stock Preload (Before) | Optimized (After) | Impact |
|---|---|---|---|
| **50th Percentile Frame Time** | **61 ms** (~16 FPS) | **19 ms** (Target: 16.6 ms for 60 FPS) | **3.2x Faster UI Rendering** |
| **90th Percentile Frame Time** | **117 ms** | **56 ms** | **2.1x Faster Navigation** |
| **99th Percentile Worst Frame** | **301 ms** | **172 ms** | **42.8% Faster Worst Case** |
| **Severe UI Freezes (>700 ms)** | **226 freeze events** | **14 freeze events** | **93.8% Reduction in lockups** |
| **Background Network CPU Storm** | **89% – 171% CPU** (Conviva OkHttp loop) | **~0% CPU** | **Eliminated retry storm** |
| **Voice Interactor Background CPU** | **25% – 50% CPU** (Hotword listener) | **0% CPU** (Mic button preserved) | **Zero idle CPU waste** |
| **Launcher RAM Footprint** | **~180 MB** (Stock Google TV) | **54 MB** (`com.gathin.tvhome`) | **70% Less launcher RAM** |

---

## 3. The Five Core Optimizations

### Optimization 1: Enforce Strict Background Process Limits

Budget TVs cannot sustain multi-tasking in RAM. Giving the active streaming app exclusive access to system memory prevents frame drops and UI freezes.

```bash
# Force Android to limit cached background apps to 1:
adb shell settings put global standard_background_process_limit 1
```

*Result:* The moment you launch Sling, Netflix, or YouTube, Android gracefully clears idle cached apps out of the app heap, leaving the entire ~550 MB sandbox for the active foreground app.

---

### Optimization 2: Throttle Voice Assistant Background Polling

Google Assistant (`com.google.android.katniss:interactor`) attempts to run an always-listening hotword microphone loop in software on TVs lacking hardware DSPs. On budget quad-core SoCs, this consumes **25% to 50% of an entire CPU core** around the clock.

```bash
# Restrict Katniss from running unconstrained in the background:
adb shell cmd appops set com.google.android.katniss RUN_IN_BACKGROUND ignore
adb shell cmd appops set com.google.android.katniss RUN_ANY_IN_BACKGROUND ignore
```

*Result:* Background CPU drops to 0%. **The physical Google Assistant microphone button on the remote still works instantaneously** when pressed.

---

### Optimization 3: Fix DNS Telemetry Sinkholes (AdGuard Home / Pi-hole)

If your TV sits behind a network-level adblocker (AdGuard Home, Pi-hole, pfSense), you may experience severe, unexplainable UI freezing on streaming apps like Sling TV.

#### The Problem:
Apps like Sling TV use analytics frameworks (Conviva `cws.conviva.com` and MoveTV `tics.movetv.com`). When AdGuard Home blocks these using the **Default `127.0.0.1` Sinkhole**, the app receives an immediate `ECONNREFUSED` on port 443. Sling's OkHttp library treats this as a network disconnect and spins an aggressive infinite retry loop across 4 threads, consuming **up to 171% CPU load** and triggering 850 ms GC freezes.

#### The Fix:
1. **Change Blocking Mode to `NXDOMAIN`:**
   In AdGuard Home (*Settings → DNS settings → Blocking mode*), select **NXDOMAIN**.  
   *Why:* Returning `NXDOMAIN` (non-existent domain) signals to OkHttp that the host does not exist, causing it to back off and fail gracefully without retrying.
2. **Or Add DNS Allowlist Exceptions:**
   If using strict domain blocking, add allowlist exceptions for the streaming service's metrics endpoints:
   ```text
   @@||cws.conviva.com^
   @@||movetv.com^
   ```

---

### Optimization 4: Ahead-Of-Time (AOT) Native Code Compilation

By default, Android TV uses a Just-In-Time (JIT) compiler with profile-guided compilation (`speed-profile`). This requires the low-power CPU to compile bytecode into machine instructions while you are trying to stream 4K video, while also occupying valuable RAM for JIT code caches.

Compile heavy streaming apps to native ARM machine instructions ahead of time:

```bash
# Precompile Sling TV to native ARM machine instructions:
adb shell cmd package compile -m speed -f com.sling

# Optional: Precompile other heavy media apps:
adb shell cmd package compile -m speed -f com.google.android.youtube.tv
adb shell cmd package compile -m speed -f com.netflix.ninja
```

*Result:* App startup times decrease by 30–50%, navigation is noticeably snappier, and in-memory JIT caching overhead is eliminated.

---

### Optimization 5: Disable Manufacturer & Recommendation Bloat

Stock TV skins run continuous recommendation scrapers that poll content providers and cache promotional artwork.

```bash
# Apply model-specific debloat list:
tools/disable.sh examples/tcl-43s434/debloat.txt
```

> [!CAUTION]
> **Never disable hardware display dependencies!**  
> On TCL Realtek RTD2851 platforms (`AR05` firmware), `com.tcl.gallery` and `com.tcl.ui_mediaCenter` are bound to `TV_INPUT_HARDWARE`. Disabling them will cause black screens on HDMI inputs. Always check your model's notes in `examples/`.

---

## 4. Automation Script

To apply all system-level optimizations (process limits, AppOps restrictions, and AOT compilation) in one pass, run:

```powershell
# Windows PowerShell
powershell -File .\tools\optimize-low-end.ps1 -TvHost <TV_IP>
```

```bash
# macOS / Linux
bash tools/optimize-low-end.sh <TV_IP>
```
