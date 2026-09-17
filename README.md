# PattNG

v2rayNG fork for Iranians

**تغییرات v2rayNG:**

* اضافه شدن cipherSuites و فینگرپرینت unsafe در تنظیمات و شیرلینک.

* اضافه شدن هسته‌ی aether

**تغییرات Xray-core:**

* امکان اتصال به کانفیگ‌های غیر رمزنگاری شده برای آدرس‌های عمومی در VLESS و TROJAN

## Server country flags

Server rows can show a country flag. There are two independent sources and they must not be
confused:

- **Ingress (per server row)**: a hint resolved locally from the profile label (country
  names, emoji flags, uppercase ISO codes) or by geolocating the server's public address
  through `https://ipwho.is` (returned `country_code`). The label and server flags remain
  separate even when they disagree. This says where you connect *to*, which can be a
  CDN/relay entrance different from the actual exit country.
- **Exit (bottom bar only)**: the country reported for the current connection test's exit
  IP, shown next to the status text. It is measured for the active connection and is never
  written onto a profile row.

Unknown countries stay blank; the OS locale is never substituted. Private, reserved,
loopback, CGNAT, documentation and otherwise special-purpose addresses never reach the
network, and profile names, credentials or configuration content are never sent anywhere.
Only a hostname's resolved public IP is submitted, never the hostname. Lookups bypass the
app's configured HTTP proxy (an active device VPN may still route them). Work is limited
to the first 128 rows of the selected, filtered group, with one request at a time, at least
1.1 seconds between requests, a 256-entry memory cache (24-hour success / 5-minute failure),
and cancellation when the group changes or the ViewModel ends. Platform DNS has a bounded
worker and queue because an OS DNS call may ignore interruption. No country is persisted
in a profile or used to alter routing, scanning, or connection configuration.

Bundled flag artwork: `app/src/main/assets/country_flags/`, generated from
[hampusborgos/country-flags](https://github.com/hampusborgos/country-flags) (Wikimedia
Commons, flags in the public domain); see `NOTICE.txt` for revision and provenance.

## حمایت

اگر کارهای بنده باعث دسترسی شما به اینترنت آزاد شده است ممنون میشم حمایتی هم از اینجانب انجام دهید

`USDT (BEP20)`: 0x76a768B53Ca77B43086946315f0BDF21156bF424

`USDT (TRC20)`: TU5gKvKqcXPn8itp1DouBCwcqGHMemBm8o

`TON (TON)`: UQAc-mZB3y7uxWHKiMmq0ORZEYgycWDWZ4V1k73HsXvTJx-i

@patterniha
