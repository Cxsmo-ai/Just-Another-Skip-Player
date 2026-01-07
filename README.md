<p align="center">
  <img src="assets/jasp_banner.png" alt="JASP Banner" width="600"/>
</p>

<h1 align="center">🎬 Just Another Skip Player</h1>

<p align="center">
  <b>Simple and lightweight, yet polished and powerful Android video player based on ExoPlayer</b><br>
  <i>Skip intros, track progress, submit timestamps - all in one beautiful player</i>
</p>

<p align="center">
  <a href="https://github.com/Cxsmo-ai/Just-Another-Skip-Player/releases/latest">
    <img src="https://img.shields.io/github/v/release/Cxsmo-ai/Just-Another-Skip-Player?style=for-the-badge&logo=android&logoColor=white&label=Latest%20Release&color=00C853" alt="Latest Release"/>
  </a>
  <a href="https://github.com/Cxsmo-ai/Just-Another-Skip-Player/releases">
    <img src="https://img.shields.io/github/downloads/Cxsmo-ai/Just-Another-Skip-Player/total?style=for-the-badge&logo=download&logoColor=white&label=Downloads&color=2196F3" alt="Downloads"/>
  </a>
  <a href="https://discord.gg/njSKPUQtFa">
    <img src="https://img.shields.io/badge/Discord-Join-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"/>
  </a>
</p>

<p align="center">
  <a href="https://github.com/Cxsmo-ai/Just-Another-Skip-Player/actions/workflows/build.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/Cxsmo-ai/Just-Another-Skip-Player/build.yml?style=for-the-badge&logo=github-actions&logoColor=white&label=Build" alt="Build Status"/>
  </a>
  <img src="https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Mobile-green?style=for-the-badge&logo=android&logoColor=white" alt="Platform"/>
  <img src="https://img.shields.io/badge/API-21%2B-blue?style=for-the-badge&logo=android&logoColor=white" alt="Min API"/>
</p>

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 🎯 Smart Intro Skipping
> **5-tier skip detection system**

- 🔥 **AnimeSkip** - Best anime database
- 📺 **SkipDB** - TV show intros
- ⚡ **IntroHater** - Community-powered (requires debrid API key)
- 🇯🇵 **AniSkip** - Anime fallback
- 📊 **IntroDB** - Auto-submit + manual submission buttons

</td>
<td width="50%">

### 📊 Trakt Integration
> **Track everything you watch**

- ✅ Auto-scrobble on 80% completion
- ⏸️ Progress sync on pause
- 📈 Watch history tracking
- 🔔 Optional toast notifications
- 🔐 OAuth2 authentication

</td>
</tr>
<tr>
<td width="50%">

### 🌐 Remote Web UI
> **Submit intro timestamps from your phone**

- 📱 Access at `http://TV_IP:8355`
- ⏱️ Mark intro start/end times
- 📤 Submit to IntroDB
- 💻 Works on any browser

</td>
<td width="50%">

### 🎥 Format Support
> **Play anything**

- MKV, MP4, AVI, WebM
- H.265/HEVC, VP9, AV1
- Dolby Vision & HDR10+
- DTS, Dolby Atmos
- SSA/ASS subtitles (partial)

</td>
</tr>
</table>

---

## 📥 Installation

### Download APK

| Version | Description | Download |
|---------|-------------|----------|
| **JASP** | New purple icon + JASP name | [![Download JASP](https://img.shields.io/badge/Download-JASP-00C853?style=for-the-badge&logo=android)](https://github.com/Cxsmo-ai/Just-Another-Skip-Player/releases/latest/download/jasp-universal-release.apk) |
| **Original** | Classic Just Player icon + name | [![Download Original](https://img.shields.io/badge/Download-Original-2196F3?style=for-the-badge&logo=android)](https://github.com/Cxsmo-ai/Just-Another-Skip-Player/releases/latest/download/original-universal-release.apk) |

> **Both versions have identical features** - only the app icon and name differ!

---

## ⚙️ Configuration

### 🔑 API Keys

| Service | Purpose | Required? |
|---------|---------|-----------|
| **IntroHater** | Skip intros/outros | ✅ Uses your debrid API key |
| **IntroDB** | Submit timestamps | ❌ Optional |

> **IntroHater uses your existing debrid service API key (TorBox, Real-Debrid, AllDebrid, or Premiumize) - no separate registration needed!**

| Debrid Service | Where to Get Key |
|----------------|------------------|
| TorBox | `torbox.app` → Account → API |
| Real-Debrid | `real-debrid.com/apitoken` |
| AllDebrid | `alldebrid.com` → Account → API |
| Premiumize | `premiumize.me` → Account |

### 📺 Trakt Setup

1. Go to `trakt.tv/oauth/applications/new`
2. Create application, get Client ID
3. Enter in Player Settings → Trakt
4. Authorize with your account

---

## 🎮 Usage as External Player

JASP works with **any app that supports external players**:

- **Stremio** → Settings → Advanced → External Player
- **Syncler** → Settings → Player → External Player
- **Kodi** → Settings → Player → Videos
- **And more!**

---

## 🛠️ Building from Source

```bash
git clone https://github.com/Cxsmo-ai/Just-Another-Skip-Player.git
cd Just-Another-Skip-Player

# Build JASP version
./gradlew assembleJaspLatestUniversalDebug

# Build Original version
./gradlew assembleOriginalLatestUniversalDebug
```

---

## 🤝 Credits

<table>
<tr>
<td align="center">
<b>Created by</b><br>
<a href="https://discord.gg/njSKPUQtFa">
<img src="https://img.shields.io/badge/Cxsmo__AI-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Cxsmo_AI"/>
</a>
</td>
<td align="center">
<b>Based on</b><br>
<a href="https://github.com/moneytoo/Player">
<img src="https://img.shields.io/badge/moneytoo%2FPlayer-181717?style=for-the-badge&logo=github&logoColor=white" alt="moneytoo/Player"/>
</a>
</td>
</tr>
</table>

---

## 📄 License

```
Fork of moneytoo/Player - Licensed under GPL-3.0
```

<p align="center">
  <a href="https://discord.gg/njSKPUQtFa">
    <img src="https://img.shields.io/badge/Join%20our%20Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join Discord"/>
  </a>
</p>

<p align="center">
  <sub>Made with ❤️ by Cxsmo_AI</sub>
</p>
