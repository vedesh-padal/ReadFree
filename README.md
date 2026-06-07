# ReadFree 📖

Read any Medium article for free — share a link, the app proxies it through a Freedium mirror and renders it inside a WebView. No browser redirect. No paywall.

---

## How it works

1. **Share** a Medium link from any app (Chrome, Twitter, etc.) → select **ReadFree** from the share sheet
2. **Or paste** a URL directly from the home screen
3. **Or tap** a medium.com link — ReadFree will appear as an option if the Medium app is not installed. If the Medium app is installed, it owns the domain via App Links and will open instead (use the share sheet in that case)

The article is loaded through a Freedium-compatible mirror. If the primary mirror fails, the app automatically tries the next one. A gear icon in the toolbar lets you set your own preferred mirror URL.

---

## Features

- Share-sheet integration — catches `text/plain` shares from any app
- Direct medium.com link interception
- In-app WebView reader — no browser handoff
- Centered loading overlay with status text
- Auto-failover across multiple Freedium mirrors on network/HTTP errors
- SSL error dialog — warns you before bypassing a bad certificate
- **Mirror settings** — pick a preset or enter a custom mirror URL; persisted across sessions
- "Open in Browser" via Chrome Custom Tab
- Dark theme, foldable/split-screen safe

---

## Get the APK

### Option 1: GitHub Actions (no local setup needed)

1. Fork or push this repo to your GitHub account
2. Go to the repo → **Actions** tab
3. The **Build APK** workflow runs automatically on every push (~2–3 min)
4. Click the completed run → **Artifacts** → download **ReadFree-debug**
5. Unzip → transfer `app-debug.apk` to your phone → install
   > Enable **"Install from unknown sources"** in your phone's settings first

You can also trigger it manually: **Actions → Build APK → Run workflow**

### Option 2: Build locally

Requires JDK 17 and Android SDK (platform 34, build-tools 34).

```bash
chmod +x gradlew
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Changing the mirror

Tap the **⚙ gear icon** in the reader toolbar → pick a preset or type your own URL → Apply.

No rebuild needed.

---

## Requirements

- Android 8.0+ (API 26)
- Internet permission only — no other permissions requested
