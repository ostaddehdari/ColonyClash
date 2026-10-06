# Colony Clash — Real APK Build

This repository is prepared to end every stage with installable Android APK artifacts.

## Option A — GitHub Actions (recommended)

1. Put this repository on GitHub.
2. Open **Actions → Colony Clash CI → Run workflow**.
3. Set `api_base_url` to the HTTPS backend URL (or leave the placeholder for UI-only smoke build).
4. When the run passes, download artifact **colony-clash-debug-apks**.

Artifacts:
- `ColonyClash-play-debug.apk`
- `ColonyClash-iran-debug.apk`
- `ColonyClash-china-debug.apk`
- `SHA256SUMS.txt`

## Option B — Ubuntu build server

Run once:

```bash
sudo bash scripts/bootstrap-ubuntu-build-server.sh
source /etc/profile.d/android-sdk.sh
source /etc/profile.d/gradle.sh
```

Build each stage:

```bash
API_BASE_URL=https://api.example.com bash scripts/build-stage.sh
```

Outputs are written to `dist/`.

## Install on a real Android phone

Enable **Developer options → USB debugging**, connect the phone, then:

```bash
adb devices
bash scripts/install-apk.sh play
```

Use `iran` or `china` instead of `play` for those flavors.

## Release AAB

Do not use debug signing for Google Play. Release signing, Play App Signing, Billing verification, and production AAB are intentionally handled in the release/billing stages.
