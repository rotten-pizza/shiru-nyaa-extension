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

If every Nyaa host fails (ISP block, CORS, timeout), the extension falls back to [Anime Tosho](https://animetosho.org)'s JSON feed, which mirrors Nyaa uploads. Category and trusted-only filters can't be applied to fallback results.

Results are sorted by Shiru using seeders, leechers, size, and the `accuracy` hint (Trusted → `high`, Remake → `low`, otherwise `medium`).

## License

GPL-3.0
