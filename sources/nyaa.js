import AbstractSource from './abstract.js'

// Appended to every magnet link so the torrent client can find peers before DHT warms up.
// More working trackers means more peers, which matters most when the client can't accept
// incoming connections (e.g. behind a VPN with no port forwarding, as on Android).
// Refreshed from github.com/ngosang/trackerslist (trackers_best) in Oct 2026; the dead
// coppersurfer.tk / openbittorrent.com entries were removed.
const TRACKERS = [
  // Nyaa's own tracker: every Nyaa upload announces here.
  'http://nyaa.tracker.wf:7777/announce',
  'udp://tracker.opentrackr.org:1337/announce',
  'udp://open.stealth.si:80/announce',
  'udp://tracker.torrent.eu.org:451/announce',
  'udp://open.demonii.com:1337/announce',
  'udp://open.tracker.cl:1337/announce',
  'udp://exodus.desync.com:6969/announce',
  'udp://explodie.org:6969/announce',
  'udp://tracker.qu.ax:6969/announce',
  'udp://tracker-udp.gbitt.info:80/announce',
  'udp://tracker.gmi.gd:6969/announce',
  // HTTP trackers still work on networks that drop UDP.
  'http://tracker.opentrackr.org:1337/announce',
  'http://tracker.dler.org:6969/announce',
  // IP-addressed copies of opentrackr and torrent.eu.org, in case the ISP's DNS blocks
  // tracker hostnames.
  'udp://93.158.213.92:1337/announce',
  'udp://91.216.110.53:451/announce'
]

const CATEGORIES = {
  english: '1_2',
  raw: '1_4',
  nonEnglish: '1_3',
  all: '1_0'
}

const FALLBACK_HOSTS = [
  'https://nyaa.si'
]

const REQUEST_TIMEOUT_MS = 8000

// Anime Tosho mirrors every Nyaa upload and serves CORS-enabled JSON, so it still works
// when nyaa.si is blocked by the ISP or its RSS is rejected by the browser's CORS policy.
const TOSHO_URL = 'https://feed.animetosho.org/json'

function parseSize (input) {
  if (!input) return 0
  const match = String(input).match(/([\d.]+)\s*(KiB|MiB|GiB|TiB|KB|MB|GB|TB|B)/i)
  if (!match) return 0
  const value = parseFloat(match[1])
  const unit = match[2].toLowerCase()
  const multipliers = {
    b: 1,
    kib: 1024, kb: 1024,
    mib: 1024 ** 2, mb: 1024 ** 2,
    gib: 1024 ** 3, gb: 1024 ** 3,
    tib: 1024 ** 4, tb: 1024 ** 4
  }
  return Math.round(value * (multipliers[unit] ?? 1))
}

function decodeEntities (s) {
  if (!s) return ''
  return s
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
}

// Shiru on Android replaces fetch with a bridge that postMessages the request options
// to the main thread. An AbortSignal cannot be structured-cloned, so passing one makes
// every request throw a DataCloneError. Time out with Promise.race instead.
function fetchWithTimeout (url, ms = REQUEST_TIMEOUT_MS) {
  let timer
  const timeout = new Promise((_resolve, reject) => {
    timer = setTimeout(() => reject(new Error(`Request to ${url} timed out after ${ms}ms`)), ms)
  })
  return Promise.race([fetch(url), timeout]).finally(() => clearTimeout(timer))
}

// Shiru passes several titles per show (romaji, English, synonyms). Nyaa searches with
// Elasticsearch's simple_query_string, where " | * ( ) ~ + are operators and a word starting
// with - is an exclusion, so a title like "Re:Zero -Starting Life in Another World-" would
// exclude "Starting". Strip those, plus punctuation that only gets in the way.
const MAX_TITLE_QUERIES = 4

function cleanTitle (title) {
  return String(title ?? '')
    .replace(/["|*()~+:;!?,[\]{}<>\/]/g, ' ')
    .replace(/(^|\s)-+/g, '$1')
    .replace(/-+(?=\s|$)/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function titleCandidates (titles) {
  const list = []
  for (const t of titles ?? []) {
    const clean = cleanTitle(t)
    if (clean && !list.some(x => x.toLowerCase() === clean.toLowerCase())) list.push(clean)
    if (list.length >= MAX_TITLE_QUERIES) break
  }
  return list
}

function normalizeHost (raw) {
  if (!raw) return ''
  let host = String(raw).trim()
  if (!host) return ''
  if (!/^https?:\/\//i.test(host)) host = 'https://' + host
  return host.replace(/\/+$/, '')
}

export default new class NyaaSource extends AbstractSource {
  url = 'https://nyaa.si'
  settings = {}

  #category () {
    return CATEGORIES[this.settings?.category] ?? CATEGORIES.english
  }

  #filter () {
    return this.settings?.trustedOnly ? '2' : '0'
  }

  #magnet (hash, name) {
    const trackers = TRACKERS.map(t => `&tr=${encodeURIComponent(t)}`).join('')
    return `magnet:?xt=urn:btih:${hash}&dn=${encodeURIComponent(name)}${trackers}`
  }

  #buildQuery (terms, resolution, exclusions = []) {
    const parts = [terms]
    if (resolution) parts.push(`${resolution}p`)
    for (const ex of exclusions) if (ex) parts.push(`-${ex}`)
    return parts.filter(Boolean).join(' ').replace(/\s+/g, ' ').trim()
  }

  #endpoint (host, query) {
    return `${host}/?page=rss&q=${encodeURIComponent(query)}&c=${this.#category()}&f=${this.#filter()}`
  }

  #candidateHosts () {
    const custom = normalizeHost(this.settings?.baseUrl)
    const list = []
    if (custom) list.push(custom)
    for (const fb of FALLBACK_HOSTS) if (!list.includes(fb)) list.push(fb)
    return list
  }

  #parse (xml) {
    const results = []
    const itemRegex = /<item\b[^>]*>([\s\S]*?)<\/item>/gi
    let match
    while ((match = itemRegex.exec(xml)) !== null) {
      const block = match[1]
      const get = (tag) => {
        const re = new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`, 'i')
        const found = block.match(re)
        return found ? decodeEntities(found[1].trim()) : ''
      }

      const hash = get('nyaa:infoHash').toLowerCase()
      if (!/^[a-f0-9]{40}$/.test(hash)) continue

      const title = get('title')
      const trusted = get('nyaa:trusted') === 'Yes'
      const remake = get('nyaa:remake') === 'Yes'
      const pubDate = get('pubDate')

      results.push({
        title,
        link: this.#magnet(hash, title),
        hash,
        seeders: Number(get('nyaa:seeders')) || 0,
        leechers: Number(get('nyaa:leechers')) || 0,
        downloads: Number(get('nyaa:downloads')) || 0,
        size: parseSize(get('nyaa:size')),
        date: pubDate ? new Date(pubDate) : new Date(),
        accuracy: trusted ? 'high' : remake ? 'low' : 'medium'
      })
    }
    return results
  }

  async #fetchRss (host, query) {
    const res = await fetchWithTimeout(this.#endpoint(host, query))
    if (!res.ok) throw new Error(`${host} returned HTTP ${res.status}`)
    const text = await res.text()
    if (!/<rss\b|<channel\b|<item\b/i.test(text)) {
      throw new Error(`${host} did not return RSS (got ${text.slice(0, 60).replace(/\s+/g, ' ')}…)`)
    }
    return text
  }

  async #searchTosho (terms, resolution, exclusions = []) {
    const query = this.#buildQuery(terms, resolution)
    const res = await fetchWithTimeout(`${TOSHO_URL}?q=${encodeURIComponent(query)}`)
    if (!res.ok) throw new Error(`Anime Tosho returned HTTP ${res.status}`)
    const items = JSON.parse(await res.text())
    if (!Array.isArray(items)) throw new Error('Anime Tosho did not return a result list')
    const excluded = exclusions.filter(Boolean).map(e => e.toLowerCase())
    const results = []
    for (const item of items) {
      if (item?.nyaa_id == null) continue
      const hash = String(item.info_hash ?? '').toLowerCase()
      if (!/^[a-f0-9]{40}$/.test(hash)) continue
      const title = item.title ?? item.torrent_name ?? ''
      if (excluded.some(e => title.toLowerCase().includes(e))) continue
      results.push({
        title,
        link: this.#magnet(hash, title),
        hash,
        seeders: Number(item.seeders) || 0,
        leechers: Number(item.leechers) || 0,
        downloads: Number(item.torrent_downloaded_count) || 0,
        size: Number(item.total_size) || 0,
        date: item.timestamp ? new Date(item.timestamp * 1000) : new Date(),
        accuracy: 'medium'
      })
    }
    return results
  }

  async #search (terms, resolution, exclusions) {
    const query = this.#buildQuery(terms, resolution, exclusions)
    const errors = []
    for (const host of this.#candidateHosts()) {
      try {
        const xml = await this.#fetchRss(host, query)
        if (this.url !== host) this.url = host
        return this.#parse(xml)
      } catch (err) {
        errors.push(err?.message ?? String(err))
      }
    }
    try {
      return await this.#searchTosho(terms, resolution, exclusions)
    } catch (err) {
      errors.push(err?.message ?? String(err))
    }
    throw new Error(errors.join('\n'))
  }

  // Tries each title in turn and returns the first non-empty result set. Network errors are
  // rethrown straight away: if Nyaa and Anime Tosho are unreachable for one title they are
  // unreachable for all of them, and retrying would only stack up timeouts.
  async #firstHit (titles, run) {
    const candidates = titleCandidates(titles)
    if (!candidates.length) candidates.push('')
    for (const title of candidates) {
      const results = await run(title)
      if (results.length) return results
    }
    return []
  }

  async single ({ titles, resolution, episode, exclusions }) {
    const ep = episode != null ? String(episode).padStart(2, '0') : ''
    return this.#firstHit(titles, base => this.#search(`${base} ${ep}`.trim(), resolution, exclusions))
  }

  async batch ({ titles, resolution, episodeCount, exclusions }) {
    const ranges = []
    if (episodeCount) ranges.push(`01-${String(episodeCount).padStart(2, '0')}`)
    ranges.push('Batch', 'Complete', 'Season')

    return this.#firstHit(titles, async base => {
      const settled = await Promise.allSettled(
        ranges.map(r => this.#search(`${base} ${r}`.trim(), resolution, exclusions))
      )
      if (settled.every(s => s.status === 'rejected')) throw settled[0].reason
      const seen = new Set()
      const deduped = []
      for (const s of settled) {
        if (s.status !== 'fulfilled') continue
        for (const r of s.value) {
          if (seen.has(r.hash)) continue
          seen.add(r.hash)
          deduped.push({ ...r, type: 'batch' })
        }
      }
      return deduped
    })
  }

  async movie ({ titles, resolution, exclusions }) {
    return this.#firstHit(titles, base => this.#search(base, resolution, exclusions))
  }

  // Throws instead of returning false so Shiru's error toast shows why each source failed
  // (Shiru otherwise only says "The content source appears to be unreachable").
  async validate () {
    const errors = []
    for (const host of this.#candidateHosts()) {
      try {
        await this.#fetchRss(host, 'test')
        this.url = host
        return true
      } catch (err) {
        const msg = err?.message ?? String(err)
        errors.push(msg.includes(host) ? msg : `${host}: ${msg}`)
      }
    }
    try {
      const res = await fetchWithTimeout(`${TOSHO_URL}?q=test`)
      if (!res.ok) throw new Error(`Anime Tosho returned HTTP ${res.status}`)
      if (!Array.isArray(JSON.parse(await res.text()))) throw new Error('Anime Tosho did not return a result list')
      return true
    } catch (err) {
      errors.push(`Anime Tosho: ${err?.message ?? String(err)}`)
    }
    throw new Error(`Could not reach Nyaa or Anime Tosho. ${errors.join(' | ')}`)
  }
}()
