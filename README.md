# JustProxy

A modern Android app for routing device traffic through HTTP/SOCKS proxies — built for security researchers and network debuggers.

Supports both **Root** (iptables) and **VPN** (no-root) interception modes with a clean Material 3 interface.

---

## Features

- **Two interception modes**
  - **Root** — transparent DNAT via iptables, zero battery overhead
  - **VPN** — works without root using Android's `VpnService`
- **Proxy protocol support** — HTTP, SOCKS4, SOCKS5
- **Per-app targeting** — route only selected apps through the proxy
- **System proxy mode** — sets the global Android HTTP proxy (root only)
- **Traffic Log** — real-time connection log with TLS inspection
  - TLS version detection (1.0 / 1.2 / 1.3)
  - SSL pinning detection (`certificate_unknown`, `bad_certificate`)
  - ECH (Encrypted Client Hello) detection
  - GREASE cipher detection
  - TLS Alert parsing with alert code lookup
- **Profile management** — save, edit, and switch between proxy profiles with custom emoji icons
- **Synthetic TLS alert injection** — improves Burp Suite logging for SSL-pinned apps
- **Persistent notification** with quick-stop action
- **Dark / Light theme** with 8 accent color presets
- **Language support** — English and Turkish

---

## Screenshots

> *Add screenshots here*

---

## Requirements

| | |
|---|---|
| Android | 7.0+ (API 24) |
| Root mode | Rooted device with `su` |
| VPN mode | Any device, no root |

---

## Installation

### From source

```bash
git clone https://github.com/whoismept/justproxy.git
cd justproxy
./gradlew assembleDebug
```

Install the APK at `app/build/outputs/apk/debug/app-debug.apk`.

### First launch

On first launch, JustProxy checks for root access and asks you to select an interception mode. This can be changed later from **Settings → Proxy → Interception Mode**.

---

## Usage

### 1. Create a profile

Tap **+** on the Profiles screen and fill in:

| Field | Description |
|---|---|
| Name | Label shown on the card |
| Host | Proxy server address (e.g. `192.168.1.5`) |
| Port | Proxy port (e.g. `8080`) |
| Type | HTTP / SOCKS4 / SOCKS5 |
| Target Apps | Leave empty for global interception, or pick specific apps |
| System Proxy | Enable to use Android's global HTTP proxy setting (root + HTTP only) |

### 2. Start intercepting

Toggle the switch on a profile card. The proxy starts in the background with a persistent notification. Tap **Stop** in the notification to stop.

### 3. View traffic

Enable **Traffic Log Tab** in Settings, then open the **Log** tab to inspect live connections. Tap a row to expand TLS details.

---

## Architecture

```
app/
├── data/               # Room database, entities, DAO, repository
├── service/
│   ├── ProxyService    # Root mode foreground service
│   ├── ProxyVpnService # VPN mode foreground service
│   ├── LocalProxyRelay # TCP relay with TLS/HTTP inspection
│   └── vpn/            # Packet-level VPN plumbing (IP/TCP/UDP)
├── ui/
│   ├── screens/        # Profiles, Log, Settings, Info
│   ├── components/     # ProfileCard, ProxyEditDialog, AppPickerDialog
│   ├── Navigation.kt   # Bottom nav + theme engine
│   ├── SplashScreen.kt # First-run mode selector
│   ├── L10n.kt         # In-app localization (EN / TR)
│   └── ProxyViewModel  # MVVM ViewModel
└── utils/
    ├── ProxyManager    # iptables rule management
    └── RootHelper      # Root command execution
```

**Stack:** Kotlin · Jetpack Compose · Material 3 · Room · Coroutines · MVVM

---

## Open Source Libraries

| Library | License |
|---|---|
| Jetpack Compose | Apache 2.0 |
| Compose Material3 | Apache 2.0 |
| Compose Material Icons | Apache 2.0 |
| AndroidX Core KTX | Apache 2.0 |
| AndroidX Lifecycle | Apache 2.0 |
| AndroidX Activity Compose | Apache 2.0 |
| Room | Apache 2.0 |
| Kotlin Coroutines | Apache 2.0 |

---

## Disclaimer

JustProxy is intended for **security research, penetration testing, and network debugging** on devices you own or have explicit permission to test. Using this tool to intercept traffic without authorization may violate applicable laws. Use responsibly.

---

## License

```
MIT License

Copyright (c) 2024 whoismept

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
