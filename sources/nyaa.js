import AbstractSource from './abstract.js'

const TRACKERS = [
  'http://nyaa.tracker.wf:7777/announce',
  'udp://open.stealth.si:80/announce',
  'udp://tracker.opentrackr.org:1337/announce',
  'udp://exodus.desync.com:6969/announce',
  'udp://tracker.torrent.eu.org:451/announce',
  'udp://tracker.coppersurfer.tk:6969/announce',
  'udp://9.rarbg.to:2710/announce',
  'udp://tracker.leechers-paradise.org:6969/announce'
]

const CATEGORIES = {
  english: '1_2',
  raw: '1_4',
  nonEnglish: '1_3',
  all: '1_0'
}

const NYAA_NS = 'https://nyaa.si/xmlns/nyaa'

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
    return parts.filter(Boolean).join(' ')
  }

  #parse (xml) {
    const doc = new DOMParser().parseFromString(xml, 'text/xml')
    if (doc.querySelector('parsererror')) throw new Error('Nyaa returned malformed RSS')

    const items = Array.from(doc.getElementsByTagName('item'))
    return items.map(item => {
      const get = tag => item.getElementsByTagName(tag)[0]?.textContent?.trim() ?? ''
      const getNs = tag => item.getElementsByTagNameNS(NYAA_NS, tag)[0]?.textContent?.trim() ?? ''

      const title = get('title')
      const hash = getNs('infoHash').toLowerCase()
      const trusted = getNs('trusted') === 'Yes'
      const remake = getNs('remake') === 'Yes'

      return {
        title,
        link: this.#magnet(hash, title),
        hash,
        seeders: Number(getNs('seeders')) || 0,
        leechers: Number(getNs('leechers')) || 0,
        downloads: Number(getNs('downloads')) || 0,
        size: parseSize(getNs('size')),
        date: get('pubDate') ? new Date(get('pubDate')) : new Date(),
        accuracy: trusted ? 'high' : remake ? 'low' : 'medium'
      }
    }).filter(r => /^[a-f0-9]{40}$/.test(r.hash))
  }

  async #search (query) {
    const url = `${this.url}/?page=rss&q=${encodeURIComponent(query)}&c=${this.#category()}&f=${this.#filter()}`
    const res = await fetch(url)
    if (!res.ok) throw new Error(`Nyaa returned HTTP ${res.status}`)
    return this.#parse(await res.text())
  }

  async single ({ titles, resolution, episode, exclusions }) {
    const base = titles?.[0] ?? ''
    const ep = episode != null ? String(episode).padStart(2, '0') : ''
    const query = this.#buildQuery(`${base} ${ep}`.trim(), resolution, exclusions)
    return this.#search(query)
  }

  async batch ({ titles, resolution, episodeCount, exclusions }) {
    const base = titles?.[0] ?? ''
    const ranges = []
    if (episodeCount) ranges.push(`01-${String(episodeCount).padStart(2, '0')}`)
    ranges.push('Batch', 'Complete', 'Season')

    const queries = ranges.map(r => this.#buildQuery(`${base} ${r}`, resolution, exclusions))
    const settled = await Promise.allSettled(queries.map(q => this.#search(q)))
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
    const query = this.#buildQuery(titles?.[0] ?? '', resolution, exclusions)
    return this.#search(query)
  }

  async validate () {
    try {
      const res = await fetch(`${this.url}/?page=rss&q=test`)
      return res.ok
    } catch {
      return false
    }
  }
}()
