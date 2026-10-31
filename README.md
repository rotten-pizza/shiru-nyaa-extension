# Shiru Nyaa Extension

A torrent source extension for [Shiru](https://github.com/RockinChaos/Shiru) that searches [nyaa.si](https://nyaa.si) via its public RSS feed.

No account, API key, or login required.

## Install

In Shiru: **Settings → Extensions → Add Extension**, then paste:

```
gh:rotten-pizza/shiru-nyaa-extension
```

## Settings

| Setting | Description |
|---|---|
| Mirror URL | Nyaa instance to query first (use a mirror if your ISP blocks nyaa.si) |
| Category | English-translated / Non-English / Raw / All |
| Trusted Uploaders Only | Restrict results to Nyaa's trusted uploader flag |

## What it does

| Shiru method | Nyaa query |
|---|---|
| `single` | `<title> <NN> <res>p` |
| `batch` | `<title> 01-NN`, `<title> Batch`, `<title> Complete`, `<title> Season` (merged + deduped) |
| `movie` | `<title> <res>p` |

Shiru passes several titles per show (romaji, English, synonyms). The extension tries up to 4 of them in order and stops at the first one with results. Punctuation that Nyaa's search treats as operators (`: " | * ( ) ~ +`, and `-` at the start or end of a word) is stripped first, so `Re:Zero -Starting Life in Another World-` doesn't exclude "Starting".

Magnet links carry Nyaa's own tracker plus a list of currently working public trackers (UDP, HTTP, and IP-addressed copies in case the ISP's DNS blocks tracker hostnames). More trackers help the client find more peers, but they can't make up for a closed incoming port: behind a VPN without port forwarding (e.g. Proton VPN on Android), expect fewer connected peers than seeders.

If every Nyaa host fails (ISP block, CORS, timeout), the extension falls back to [Anime Tosho](https://animetosho.org)'s JSON feed, which mirrors Nyaa uploads. Category and trusted-only filters can't be applied to fallback results.

Results are sorted by Shiru using seeders, leechers, size, and the `accuracy` hint (Trusted → `high`, Remake → `low`, otherwise `medium`).

## License

GPL-3.0
