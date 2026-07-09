import AbstractSource from './abstract.js'

const TRACKERS = [
  'http://nyaa.tracker.wf:7777/announce',
  'udp://open.stealth.si:80/announce',
  'udp://tracker.opentrackr.org:1337/announce',
  'udp://exodus.desync.com:6969/announce',
  'udp://tracker.torrent.eu.org:451/announce',
  'udp://tracker.coppersurfer.tk:6969/announce',
  'udp://tracker.openbittorrent.com:6969/announce',
  'udp://tracker.dler.org:6969/announce'
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

async function fetchWithTimeout (url, ms = REQUEST_TIMEOUT_MS) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), ms)
  try {
    return await fetch(url, { signal: controller.signal })
  } catch (err) {
    if (err?.name === 'AbortError') throw new Error(`Request to ${url} timed out after ${ms}ms`)
    throw err
  } finally {
    clearTimeout(timer)
  }
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

  async #search (query) {
    let lastErr
    for (const host of this.#candidateHosts()) {
      try {
        const xml = await this.#fetchRss(host, query)
        if (this.url !== host) this.url = host
        return this.#parse(xml)
      } catch (err) {
        lastErr = err
      }
    }
    if (lastErr) throw lastErr
    return []
  }

  async single ({ titles, resolution, episode, exclusions }) {
    const base = titles?.[0] ?? ''
    const ep = episode != null ? String(episode).padStart(2, '0') : ''
    return this.#search(this.#buildQuery(`${base} ${ep}`.trim(), resolution, exclusions))
  }

  async batch ({ titles, resolution, episodeCount, exclusions }) {
    const base = titles?.[0] ?? ''
    const ranges = []
    if (episodeCount) ranges.push(`01-${String(episodeCount).padStart(2, '0')}`)
    ranges.push('Batch', 'Complete', 'Season')

    const settled = await Promise.allSettled(
      ranges.map(r => this.#search(this.#buildQuery(`${base} ${r}`, resolution, exclusions)))
    )
    const all = settled.flatMap(s => s.status === 'fulfilled' ? s.value : [])

    const seen = new Set()
    const deduped = []
    for (const r of all) {
      if (seen.has(r.hash)) continue
      seen.add(r.hash)
      deduped.push({ ...r, type: 'batch' })
    }
    return deduped
  }

  async movie ({ titles, resolution, exclusions }) {
    return this.#search(this.#buildQuery(titles?.[0] ?? '', resolution, exclusions))
  }

  async validate () {
    for (const host of this.#candidateHosts()) {
      try {
        const res = await fetchWithTimeout(`${host}/?page=rss&q=test`)
        if (!res.ok) continue
        const text = await res.text()
        if (/<rss\b|<channel\b/i.test(text)) {
          this.url = host
          return true
        }
      } catch {}
    }
    return false
  }
}()
