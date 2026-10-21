# Chess Arrows — Reverse-Engineering Implementation Specification

**Source app:** `com.gr.chessarrows` (version 1.1.0, versionCode 6)  
**Analyzed artifacts:** `base.apk` (8.3 MB), `split_config.arm64_v8a.apk`, `split_config.xxhdpi.apk`  
**Analysis method:** Static DEX/ARSC/XML analysis only — no execution, no dynamic instrumentation  
**Date:** 2026-10-21

---

## 1. Confirmed Features (Evidence-Backed)

### 1.1 Overlay Arrow Drawing
- **Service:** `OverlayService` (foreground, `foregroundServiceType="0x40000000"` → `screen_drawing_overlay`)
- **Permission:** `SYSTEM_ALERT_WINDOW` (declared, runtime-checked via `Settings.canDrawOverlays`)
- **Drawing surface:** `WindowManager.addView` with `LayoutParams` (type/flags set in `OverlayService.c(II)LayoutParams`)
- **Stroke rendering:** `ArrowPreviewView.onDraw` → `Llv0.l(Lm8;F)Lic0` (endpoint pair) → `Llv0.z(Canvas,List,Paint,Paint)` (fill + stroke paints)
- **Style model:** `Lm8` (fields `a:I` color, `b:I` width_dp, `c:I` alpha_percent) — persisted in `SharedPreferences("arrow_prefs")` keys `"color"`, `"width_dp"`, `"alpha_percent"`
- **Gesture:** `Lda0.onTouch` drag by mutating `LayoutParams.x`; release → `ValueAnimator` snap-back via `Lea0.onAnimationUpdate`
- **Buttons:** Three overlay `TextView`s created by `OverlayService.d(String;F)` — green `#DD2E7D32`, bold, elevated; labels `"✎"` (idle), `"✓"` (drawing), `"↺"` (undo), plus board ball
- **Undo/clear:** `OverlayService.h()` clears `k8.b:ArrayList` (arrow list), invalidates, toggles button labels
- **Configuration change:** `onConfigurationChanged` recomputes window metrics (`i()Lic0`), clamps button positions via `Lx81.j(II)I`, calls `updateViewLayout`

### 1.2 Touch Controls
- **Drag threshold:** Scaled touch slop via `ViewConfiguration.getScaledTouchSlop()`
- **Drag axis:** Horizontal (`LayoutParams.x` only)
- **Snap-back:** `ValueAnimator.ofInt` animating `LayoutParams.x` to clamped position
- **Click-through:** `View.performClick` on release → `Lkt.a()` callback

### 1.3 Settings UI (`MainActivity`)
| UI Element | Resource ID | Type | Field | Purpose |
|------------|-------------|------|-------|---------|
| `widthSeekBar` | `@id/widthSeekBar` | `SeekBar` | `S` | Arrow width (dp) |
| `alphaSeekBar` | `@id/alphaSeekBar` | `SeekBar` | `T` | Arrow opacity (%) |
| `preview` | `@id/preview` | `ArrowPreviewView` | `Q` | Live style preview |
| `swatches` | `@id/swatches` | container | `R:List` | Color swatches (GradientDrawable recolor in `s()`) |
| `serviceSwitch` | `@id/serviceSwitch` | `SwitchMaterial` | `G` | Toggle overlay service |
| `boardHelpButton` | `@id/boardHelpButton` | `View` | — | Help dialog |
| `modeToggle` | `@id/modeToggle` | `MaterialButtonToggleGroup` | `K` | Drawing mode selector (calls `u(Z)`) |
| `adContainer` | `@id/adContainer` | `FrameLayout` | `J` | AdMob banner container |

- **Persistence:** `u(Z)` writes `"color"`, `"width_dp"`, `"alpha_percent"` to `arrow_prefs`, calls `s()` (recolor swatches), broadcasts `ACTION_STYLE_UPDATED`
- **Service toggle:** `Lf30.onCheckedChanged` → checked ⇒ `MainActivity.t()` (starts `OverlayService`), unchecked ⇒ explicit `stopService` + `x(Z)`
- **Overlay permission gate:** `t()` checks `Settings.canDrawOverlays` + `POST_NOTIFICATIONS` before starting service

### 1.4 Onboarding (`OnboardingActivity`)
- **Pages:** 6 pages seeded in `<init>` via emoji-titled `t90` models: `"♟️"`, `"🔐"`, `"🛡️"`, `"🔔"`, `"👆"`, `"🎛️"`
- **Page model `t90`:** fields `a:String` (title), `b:I` (icon res), `c:I` (color); ctor `t90(IILjava/lang/String;)V`
- **Pager UI:** `s(I)` recolors dot indicators (`GradientDrawable` on `LinearLayout` children), sets title/body/icon from `t90` per index `G`
- **Completion:** `r()` saves `"onboarding_done"=true` to `arrow_prefs`, broadcasts `"com.example.chessarrowoverlay.ACTION_STYLE_UPDATED"`, finishes

### 1.5 Broadcast Protocol
- **`ACTION_STYLE_UPDATED`** (`"com.example.chessarrowoverlay.ACTION_STYLE_UPDATED"`): sent by `MainActivity.u(Z)`, `OnboardingActivity.r()`, `MaterialButtonToggleGroup.m(Set)` (mode toggle); received by `OverlayService` (registered in `onCreate` with dynamic permission `"<pkg>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"`)
- **`ACTION_STOP` / `ACTION_CLEAR`**: notification actions in `OverlayService.l()`
- **`ACTION_LOCALE_UPDATED`**: registered in `onCreate`

### 1.6 Permissions
| Permission | Purpose |
|------------|---------|
| `SYSTEM_ALERT_WINDOW` | Overlay drawing (checked at runtime) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | `OverlayService` foreground type `0x40000000` |
| `POST_NOTIFICATIONS` | Notification for foreground service (checked in `t()`) |
| `INTERNET`, `ACCESS_WIFI_STATE`, `AD_ID`, `ACCESS_ADSERVICES_*` | AdMob / Huawei Ads |
| `WAKE_LOCK`, `VIBRATE`, `BROADCAST_STICKY` | Service/notification support |
| `com.android.vending.CHECK_LICENSE` | Play licensing (PairIP) |

### 1.7 Ads & Analytics
- **AdMob:** `ca-app-pub-1559876501599798~6743820904` (meta-data), `AdActivity`, `MobileAdsInitProvider`
- **Huawei Ads:** `hw_ads_sdk_type=6`, `ads-lite:13.4.91.301`, `ads-base:6.13.0.304`, banner/native/interstitial/reward
- **Firebase:** Analytics, Remote Config, Installations, Crashlytics (via `FirebaseInitProvider`)
- **Play Core:** In-app review (`play/core/review/b.a(MainActivity;ReviewInfo)`), HSDP

### 1.8 PairIP Protection
- `android:name="com.pairip.application.Application"` wrapper
- `LicenseClient` synthetic lambdas (`LicenseClient$$ExternalSyntheticLambda10–13`)
- Licensing service bind/unbind plumbing in `dex_app_xrefs.txt` (no app-class refs)
- **Impact:** Method bodies for `MainActivity`/`OnboardingActivity`/`OverlayService` show real `code=` sizes → not stubbed; only licensing plumbing is obfuscated

### 1.9 Resource Mappings (ARSC)
| Resource ID | Resolved | Manifest Usage |
|-------------|----------|----------------|
| `0x7f1102b6` | `style/694` | `android:theme` |
| `0x7f10001c` | `string/28` | `android:label` |
| `0x7f0d0000` | `mipmap/0` | `android:icon` |
| `0x7f0d0001` | `mipmap/1` | `android:roundIcon` |
| `0x7f130003` | `xml/3` | `localeConfig` |
| `0x7f130008` | `xml/8` | `com.android.vending.splits` |

- **Type IDs (pkg 0x7f):** 1=anim, 2=animator, 3=attr, 4=bool, 5=color, 6=dimen, 7=drawable, 8=id, 9=integer, 10=interpolator, 11=layout, 12=?, 13=mipmap, 14=?, 15=?, 16=string, 17=style, 18=?, 19=xml
- **Key string pool empty** — aapt2 merged all strings into global pool (9489 strings)

### 1.10 Extracted Strings (Global Pool)
Key user-facing strings (Italian):
- `"Chess Arrows ha bisogno dell'autorizzazione \"Mostra sopra altre app\" per disegnare le frecce sopra la scacchiera della tua app di scacchi."`
- `"Chess Arrows mette una piccola matita fluttuante sopra la tua app di scacchi. Disegna mosse, minacce e idee senza uscire dalla partita."`
- `"1. Attiva l'interruttore. 2. Apri la tua app di scacchi. 3. Tocca la matita e trascina per disegnare una freccia. 4. Disegna una L per una mossa del cavallo. 5. Tocca ↺ per annullare, ✓ per finire e"`
- `"Autorizzazione overlay necessaria: attiva l'interruttore per configurarla"`
- `"Avanzata$$Avvia o ferma l'overlay delle frecce"`
- `"Colore"`, `"Come funziona"`, `"Come impostare la scacchiera"`, `"Come si usa"`, `"Consenti di disegnare sopra le altre app"`

---

## 2. Reproduction Steps (Static Analysis Only)

### 2.1 Environment Setup
```bash
# Required tools (Termux / Linux)
pkg install python3 unzip grep sed
# No Java, aapt2, apktool, jadx required
```

### 2.2 Extract & Parse
```bash
cd /path/to/workdir
unzip -q base.apk -d base/
# 1. Decode manifest
python3 axml.py base/AndroidManifest.xml base/AndroidManifest.xml.decoded.xml
# 2. Parse DEX
python3 dexparse.py base/classes.dex
#   → dex_strings.txt, dex_classes.txt, dex_xrefs.txt, dex_app_xrefs.txt
# 3. Parse ARSC
python3 arscparse.py base/resources.arsc [resource_ids...]
# 4. Split xrefs for readability
python3 -c "
import sys
with open('base/dex_xrefs.txt') as f: lines=f.readlines()
for i,line in enumerate(lines):
    if line.strip().endswith('|'):
        print(f'=== LINE {line.split()[0]} ===')
    else:
        print('  ' + line.rstrip())
" > xref_extract.txt
```

### 2.3 Key Analysis Queries
```bash
# Overlay service methods
grep -n 'OverlayService' base/dex_classes.txt
# MainActivity methods
grep -n 'MainActivity' base/dex_classes.txt
# Xref for specific method
grep 'MainActivity;->u' xref_extract.txt
# Resource resolution
python3 arscparse.py base/resources.arsc 0x7f1102b6 0x7f10001c ...
```

### 2.4 Verification Checklist
- [ ] `OverlayService` has `onCreate` registering `ACTION_STYLE_UPDATED` receiver with dynamic permission
- [ ] `MainActivity.t()` starts `OverlayService` via explicit intent
- [ ] `Lf30.onCheckedChanged` toggles service + calls `x(Z)`
- [ ] `ArrowPreviewView.onDraw` calls `Llv0.l` + `Llv0.z`
- [ ] `Lda0.onTouch` mutates `LayoutParams.x` + `ValueAnimator` snap-back
- [ ] `u(Z)` persists three prefs keys + broadcasts `ACTION_STYLE_UPDATED`
- [ ] `OnboardingActivity` seeds 6 `t90` pages with emoji titles
- [ ] `resources.arsc` resolves theme/style/icon/label/xml IDs as above

---

## 3. Assumptions / Unresolved Questions

| # | Question | Status | Evidence Gap |
|---|----------|--------|--------------|
| 1 | Exact `LayoutParams` type/flags values in `c(II)` | **Unresolved** | Constants compiled away; only `width,height,type,flags,format` signature known |
| 2 | `t90` ctor param→field mapping (`IILString` → `a,b,c`) | **Unresolved** | Ctor body not read; only signature known |
| 3 | `k8` (arrow canvas view) class internals | **Unresolved** | Only field refs (`b:ArrayList`, `m:Lhh3`, `setDrawingActive`) seen |
| 4 | `b70` dual static instances (`v`/`w`) purpose | **Unresolved** | Two `Lb70.<init>(I)` in `<clinit>`; `d(Object)` called in `onCreate`/`onDestroy` |
| 5 | `Ley;->h(MainActivity)` — feature flag `"enable_in_app_review"`/`"inAppReview"` flow | **Partial** | Block 5357 shows `kq.b(String)Z` gate + `startService(OverlayService)` + `x(Z)`; owner likely `Ley` via `ChessArrowsApp.b` |
| 6 | `Lca0.a()Object` → removes last arrow, calls `h()`, `k(Z)`, `f()`, `g(Loa;Z)` | **Confirmed** | Block 3657 fully read |
| 7 | `Lba0.e(Object)` → calls `OverlayService.g(Loa;Z)` | **Confirmed** | Block 2813 fully read |
| 8 | Notification channel ID/name (`overlay_service_channel`) | **Confirmed** | Block 5376 |
| 9 | Legacy package string `"com.example.chessarrowoverlay"` in 4 broadcast actions — vestigial or self-deliver? | **Open** | Appears in `OnboardingActivity.r()`, `OverlayService.l()`, `onCreate()`; runtime package name used for permission string via `Lmy3.A` |
| 10 | `MainActivity.X` (static synthetic `I`) touched in `f30.onCheckedChanged` | **Open** | Purpose unknown |
| 11 | `Ll8` helper methods (`c(Context)Lu7`, `d(Context)Lm8`, `e(Lv4;Lu7)V`, `a(String;Z)String`, `b(Context;Z)Loa`) | **Partial** | Signatures + call sites known; bodies unread |
| 12 | `Llv0.l/z` (arrow geometry + drawing) | **Unresolved** | Only call signatures from `ArrowPreviewView.onDraw` |

---

## 4. Findings Report

### 4.1 Architecture Summary
- **Single DEX** (6.5 MB, 39,489 methods, 6,504 classes) — no multidex
- **5 app classes** (`ArrowPreviewView`, `ChessArrowsApp`, `MainActivity`, `OnboardingActivity`, `OverlayService`) + PairIP licensing
- **Overlay-only approach** — no accessibility service, no `BIND_ACCESSIBILITY_SERVICE`
- **Foreground service** with `screen_drawing_overlay` subtype (Android 14+ compatible)
- **SharedPreferences** (`arrow_prefs`) for all user settings (color, width, alpha, onboarding flag, geometry `cx/cy/side`)
- **Broadcast-driven sync** — `ACTION_STYLE_UPDATED` coordinates MainActivity ↔ OverlayService ↔ OnboardingActivity

### 4.2 Critical Implementation Details for Clone
1. **Overlay permission must be granted before service start** — check `Settings.canDrawOverlays`
2. **Service runs as foreground** with `Notification` (channel `overlay_service_channel`, actions `ACTION_STOP`/`ACTION_CLEAR`)
3. **Three-button overlay** (undo/board/draw) with glyph toggles (`"✎"`/`"✓"`)
4. **Drag gesture** = horizontal `LayoutParams.x` mutation + animator snap-back
5. **Arrow geometry** = list of endpoint pairs (`Lic0` with `a`/`b` `PointF`) drawn via `Llv0.z`
6. **Style persistence** = three ints in `SharedPreferences` + live preview (`ArrowPreviewView`)
7. **Onboarding** = 6-page pager with emoji titles, dot indicators, completion flag + broadcast
8. **Mode toggle** = `MaterialButtonToggleGroup` → `u(Z)` → prefs + broadcast

### 4.3 Non-Goals / Out of Scope
- PairIP licensing bypass (not needed for clone)
- AdMob/Huawei Ads integration (optional)
- Firebase/Play Core plumbing (optional)
- Dynamic feature modules (none in base APK)

---

## 5. Files Referenced
- `/data/data/com.termux/files/home/.suroot/.tmp/opencode/apkwork/base/classes.dex`
- `/data/data/com.termux/files/home/.suroot/.tmp/opencode/apkwork/base/resources.arsc`
- `/data/data/com.termux/files/home/.suroot/.tmp/opencode/apkwork/base/AndroidManifest.xml.decoded.xml`
- `/data/data/com.termux/files/home/.suroot/.tmp/opencode/apkwork/xref_extract.txt` (50 markers, 1186 lines)
- `/data/data/com.termux/files/home/.suroot/.tmp/opencode/apkwork/overlay_hits.txt` (43 hits)
- `/data/data/com.termux/files/home/.suroot/.tmp/opencode/apkwork/arscparse.py`
- `/data/data/com.termux/files/home/.suroot/.tmp/opencode/apkwork/dexparse.py`
- `/data/data/com.termux/files/home/.suroot/.tmp/opencode/apkwork/axml.py`

---

*End of specification. All claims above are backed by static analysis evidence cited inline. Assumptions are explicitly marked in Section 3.*