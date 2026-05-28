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
| Category | English-translated / Non-English / Raw / All |
| Trusted Uploaders Only | Restrict results to Nyaa's trusted uploader flag |

## What it does

| Shiru method | Nyaa query |
|---|---|
| `single` | `<title> <NN> <res>p` |
| `batch` | `<title> 01-NN`, `<title> Batch`, `<title> Complete`, `<title> Season` (merged + deduped) |
| `movie` | `<title> <res>p` |

Results are sorted by Shiru using seeders, leechers, size, and the `accuracy` hint (Trusted → `high`, Remake → `low`, otherwise `medium`).

## License

GPL-3.0
