# ReadFree 📖

A minimal Android app that bypasses Medium paywalls using freedium-mirror.cfd.

**Share any Medium link → ReadFree → read it free, inside the app.**

---

## Features
- Appears in Android share sheet for any `text/plain` share (Medium links from Chrome, Twitter, etc.)
- Handles direct medium.com link taps
- Opens article inside the app via WebView (no browser redirect)
- "Browser" button to open the current article in Chrome Custom Tab
- Paste-a-link home screen if you open the app directly
- Dark theme

---

## Get the APK — 3 ways

### Option 1: GitHub Actions (easiest, no setup)

1. Create a GitHub account if you don't have one
2. Create a new **public** repository (call it `readfree` or anything)
3. Push this project to it:
   ```bash
   cd readfree
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
   git push -u origin main
   ```
4. Go to your repo on GitHub → **Actions** tab
5. The workflow runs automatically. Wait ~3-5 minutes.
6. Click the completed workflow run → scroll down to **Artifacts** → download **ReadFree-debug**
7. Unzip → you get `app-debug.apk`
8. Transfer to phone → install (enable "Install from unknown sources" first)

> You can also trigger it manually: Actions → Build APK → Run workflow

---

### Option 2: Codemagic (no GitHub needed)

1. Go to [codemagic.io](https://codemagic.io) → sign up free
2. Connect your GitHub/GitLab or upload zip
3. Select "Android" project → click Build
4. Download APK from build artifacts

---

### Option 3: Build locally on Ubuntu

**One-time setup:**
```bash
# Install Java 17
sudo apt install openjdk-17-jdk

# Download Android command line tools
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest

# Set env vars (add to ~/.zshrc)
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Accept licenses and install required SDK components
sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

**Build:**
```bash
cd readfree
chmod +x gradlew
./gradlew assembleDebug

# APK is at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## Changing the Freedium mirror URL

If `freedium-mirror.cfd` goes down, edit this line in `MainActivity.kt`:

```kotlin
private val FREEDIUM_BASE = "https://freedium-mirror.cfd/"
```

Replace with any working mirror and rebuild.

---

## Future ideas
- Save articles locally (offline reading)
- Tags and lists
- Raindrop.io integration (share saved articles)
- Substack RSS fallback support
- Multiple paywall bypass services with auto-failover
