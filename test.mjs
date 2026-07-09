// Local test harness — stubs fetch and runs the extension against a synthetic Nyaa RSS.
// Run: node test.mjs
import nyaa from './sources/nyaa.js'

const SAMPLE_RSS = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:nyaa="https://nyaa.si/xmlns/nyaa">
  <channel>
    <title>Nyaa - "spy family" - Torrent File RSS</title>
    <description>RSS Feed for "spy family"</description>
    <link>https://nyaa.si/</link>
    <atom:link xmlns:atom="http://www.w3.org/2005/Atom" href="https://nyaa.si/?page=rss" rel="self" type="application/rss+xml"/>
    <item>
      <title>[SubsPlease] Spy x Family - 01 (1080p) [ABCDEF12].mkv</title>
      <link>https://nyaa.si/download/1500001.torrent</link>
      <guid isPermaLink="true">https://nyaa.si/view/1500001</guid>
      <pubDate>Sat, 09 Apr 2022 14:00:00 -0000</pubDate>
      <nyaa:seeders>1523</nyaa:seeders>
      <nyaa:leechers>42</nyaa:leechers>
      <nyaa:downloads>50421</nyaa:downloads>
      <nyaa:infoHash>abcdef0123456789abcdef0123456789abcdef01</nyaa:infoHash>
      <nyaa:categoryId>1_2</nyaa:categoryId>
      <nyaa:category>Anime - English-translated</nyaa:category>
      <nyaa:size>1.4 GiB</nyaa:size>
      <nyaa:comments>3</nyaa:comments>
      <nyaa:trusted>Yes</nyaa:trusted>
      <nyaa:remake>No</nyaa:remake>
      <description><![CDATA[ <a href="https://nyaa.si/view/1500001">#1500001 | [SubsPlease] Spy x Family - 01 (1080p) [ABCDEF12].mkv | 1.4 GiB | Anime - English-translated | abcdef0123456789abcdef0123456789abcdef01</a> ]]></description>
    </item>
    <item>
      <title>[Erai-raws] Spy x Family - Batch 01-12 [1080p][Multiple Subtitle]</title>
      <link>https://nyaa.si/download/1500002.torrent</link>
      <pubDate>Sat, 02 Jul 2022 18:30:00 -0000</pubDate>
      <nyaa:seeders>987</nyaa:seeders>
      <nyaa:leechers>15</nyaa:leechers>
      <nyaa:downloads>32100</nyaa:downloads>
      <nyaa:infoHash>1234567890ABCDEF1234567890abcdef12345678</nyaa:infoHash>
      <nyaa:categoryId>1_2</nyaa:categoryId>
      <nyaa:size>16.8 GiB</nyaa:size>
      <nyaa:trusted>No</nyaa:trusted>
      <nyaa:remake>No</nyaa:remake>
    </item>
    <item>
      <title>Some random remake with &amp; ampersand</title>
      <link>https://nyaa.si/download/1500003.torrent</link>
      <pubDate>Sat, 02 Jul 2022 18:30:00 -0000</pubDate>
      <nyaa:seeders>5</nyaa:seeders>
      <nyaa:leechers>2</nyaa:leechers>
      <nyaa:downloads>40</nyaa:downloads>
      <nyaa:infoHash>aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa</nyaa:infoHash>
      <nyaa:size>650 MiB</nyaa:size>
      <nyaa:trusted>No</nyaa:trusted>
      <nyaa:remake>Yes</nyaa:remake>
    </item>
    <item>
      <title>BROKEN — missing hash should be filtered out</title>
      <link>https://nyaa.si/download/0.torrent</link>
      <pubDate>Sat, 02 Jul 2022 18:30:00 -0000</pubDate>
      <nyaa:seeders>0</nyaa:seeders>
      <nyaa:leechers>0</nyaa:leechers>
      <nyaa:downloads>0</nyaa:downloads>
      <nyaa:infoHash>not-a-valid-hash</nyaa:infoHash>
      <nyaa:size>0 B</nyaa:size>
    </item>
  </channel>
</rss>`

let lastUrl = null
globalThis.fetch = async (url) => {
  lastUrl = url
  return {
    ok: true,
    status: 200,
    text: async () => SAMPLE_RSS
  }
}

function assert (cond, msg) {
  if (!cond) { console.error('  FAIL:', msg); process.exitCode = 1 }
  else        console.log('  ok  :', msg)
}

console.log('\n=== validate() ===')
nyaa.settings = {}
const ok = await nyaa.validate()
assert(ok === true, 'validate() returns true against valid RSS')
assert(lastUrl?.startsWith('https://nyaa.si/?page=rss'), 'validate hits the right URL: ' + lastUrl)

console.log('\n=== single({ titles: ["Spy x Family"], episode: 1, resolution: "1080" }) ===')
const single = await nyaa.single({ titles: ['Spy x Family'], episode: 1, resolution: '1080', exclusions: [] })
assert(single.length === 3, `parses 3 valid items, skips bad hash (got ${single.length})`)
assert(lastUrl.includes('q=Spy%20x%20Family%2001%201080p'), 'query encodes title+ep+res: ' + lastUrl)
assert(lastUrl.includes('c=1_2'), 'default category = english (1_2)')
assert(lastUrl.includes('f=0'), 'default filter = 0 (no trusted-only)')

console.log('\n--- result[0] ---')
console.log(JSON.stringify({ ...single[0], link: single[0].link.slice(0, 80) + '…' }, null, 2))

assert(single[0].hash === 'abcdef0123456789abcdef0123456789abcdef01', 'hash extracted')
assert(single[0].seeders === 1523, 'seeders parsed as number')
assert(single[0].leechers === 42, 'leechers parsed')
assert(single[0].downloads === 50421, 'downloads parsed')
assert(single[0].size === Math.round(1.4 * 1024 ** 3), `size parsed (1.4 GiB → ${single[0].size})`)
assert(single[0].accuracy === 'high', 'trusted=Yes → accuracy=high')
assert(single[0].title.includes('Spy x Family'), 'title preserved')
assert(single[0].link.startsWith('magnet:?xt=urn:btih:abcdef'), 'magnet link built with hash')
assert(single[0].link.includes('&tr=http%3A%2F%2Fnyaa.tracker.wf'), 'trackers appended (encoded)')
assert(single[0].date instanceof Date && !isNaN(single[0].date), 'pubDate → Date')

console.log('\n--- result[1] (no trust flag) ---')
assert(single[1].accuracy === 'medium', 'no trusted/remake → medium')

console.log('\n--- result[2] (remake + ampersand entity) ---')
assert(single[2].accuracy === 'low', 'remake=Yes → low')
assert(single[2].title === 'Some random remake with & ampersand', '&amp; decoded: ' + single[2].title)

console.log('\n=== batch({ titles: ["Spy x Family"], episodeCount: 12 }) ===')
const batch = await nyaa.batch({ titles: ['Spy x Family'], episodeCount: 12, resolution: '1080', exclusions: [] })
assert(batch.every(r => r.type === 'batch'), 'all batch results tagged type=batch')
assert(batch.length === 3, `dedup across 4 queries kept ${batch.length} unique`)

console.log('\n=== movie ===')
const movie = await nyaa.movie({ titles: ['Suzume'], resolution: '1080', exclusions: [] })
assert(movie.length === 3, 'movie returned results')
assert(lastUrl.includes('q=Suzume%201080p'), 'movie query: ' + lastUrl)

console.log('\n=== settings: trustedOnly + category=raw + custom baseUrl ===')
nyaa.settings = { trustedOnly: true, category: 'raw', baseUrl: 'nyaa.example.test' }
await nyaa.single({ titles: ['Test'], episode: 5, resolution: '720', exclusions: ['HEVC'] })
assert(lastUrl.startsWith('https://nyaa.example.test/'), 'custom baseUrl normalized w/ https://: ' + lastUrl)
assert(lastUrl.includes('c=1_4'), 'category=raw → c=1_4')
assert(lastUrl.includes('f=2'), 'trustedOnly → f=2')
assert(lastUrl.includes('-HEVC'), 'exclusions prefixed with -: ' + lastUrl)
assert(lastUrl.includes('05'), 'episode zero-padded: ' + lastUrl)

console.log('\n=== fallback chain on HTTP 500 ===')
let calls = 0
globalThis.fetch = async (url) => {
  calls++
  if (url.includes('nyaa.example.test')) return { ok: false, status: 500, text: async () => '' }
  return { ok: true, status: 200, text: async () => SAMPLE_RSS }
}
nyaa.settings = { baseUrl: 'nyaa.example.test' }
const fallback = await nyaa.single({ titles: ['Test'], episode: 1, resolution: '1080', exclusions: [] })
assert(calls === 2, `tried 2 hosts before succeeding (got ${calls})`)
assert(fallback.length === 3, 'fallback returned parsed results')
assert(nyaa.url === 'https://nyaa.si', 'this.url updated to working host: ' + nyaa.url)

console.log('\n=== HTML response (e.g. ISP block page) is rejected ===')
globalThis.fetch = async () => ({ ok: true, status: 200, text: async () => '<!DOCTYPE html><html><body>Blocked</body></html>' })
nyaa.settings = {}
try {
  await nyaa.single({ titles: ['Test'], episode: 1, resolution: '1080', exclusions: [] })
  console.error('  FAIL: expected throw on HTML response')
  process.exitCode = 1
} catch (err) {
  assert(/did not return RSS/.test(err.message), 'HTML response throws: ' + err.message)
}

console.log('\n=== hanging host times out and does not block resolution ===')
// Earlier hosts fail instantly; the final candidate hangs. The source must
// abort it (rather than wait forever) and surface a timeout error.
globalThis.fetch = (url, { signal } = {}) => new Promise((resolve, reject) => {
  if (!url.includes('nyaa.net')) return reject(new Error('connection refused'))
  signal?.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })))
})
nyaa.settings = {}
const start = Date.now()
try {
  await nyaa.single({ titles: ['Test'], episode: 1, resolution: '1080', exclusions: [] })
  console.error('  FAIL: expected throw on timeout')
  process.exitCode = 1
} catch (err) {
  assert(/timed out/.test(err.message), 'hanging request throws timeout error: ' + err.message)
  assert(Date.now() - start < 12000, 'timeout fires within one request budget, not forever')
}

console.log('\nDone. exit code =', process.exitCode || 0)
