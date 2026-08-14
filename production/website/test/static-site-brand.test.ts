import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { dirname, extname, join, normalize } from 'node:path'
import test from 'node:test'

const staticRoot = 'static-site'
const localizedPages = (locale: 'zh' | 'en') =>
  readdirSync(join(staticRoot, locale))
    .filter((name) => name.endsWith('.html'))
    .sort()

test('the complete 82-page bilingual site is preserved', () => {
  assert.equal(localizedPages('zh').length, 41)
  assert.equal(localizedPages('en').length, 41)
  assert.ok(existsSync(join(staticRoot, 'index.html')))
})

test('all 82 localized pages receive the shared illustrated brand shell', () => {
  for (const locale of ['zh', 'en'] as const) {
    for (const page of localizedPages(locale)) {
      const html = readFileSync(join(staticRoot, locale, page), 'utf8')
      assert.match(html, /\.\.\/css\/style\.css/)
      assert.match(html, /class="(?:[^"]*\bpage-header\b|[^"]*\bhero\b)/)
    }
  }

  const css = readFileSync(join(staticRoot, 'css/style.css'), 'utf8')
  assert.match(css, /--cream:/)
  assert.match(css, /assets\/brand\/generated\/hero-illustration-v1\.png/)
  assert.match(css, /assets\/brand\/generated\/context-chat-scene-v2\.png/)
  assert.match(css, /assets\/brand\/langou-mascot-master\.png/)
})

test('shared raster artwork is present in the deployable static tree', () => {
  for (const asset of [
    'generated/hero-illustration-v1.png',
    'generated/context-chat-scene-v2.png',
    'generated/keyboard-showcase-v2.png',
    'generated/privacy-shield-scene-v2.png',
    'langou-mascot-master.png',
  ]) {
    assert.ok(existsSync(join(staticRoot, 'assets/brand', asset)), asset)
  }
  assert.ok(existsSync(join(staticRoot, 'favicon.ico')))
})

test('mobile hero stacks the copy and artwork instead of overlapping them', () => {
  const css = readFileSync(join(staticRoot, 'css/style.css'), 'utf8')
  assert.match(css, /@media\(max-width:900px\)[\s\S]*?\.hero\{[^}]*flex-direction:column/)
})

test('the rejected Android build cannot remain on a public download surface', () => {
  const rejectedSha256 =
    'dd500206c9b245bd2310c9dcdbd4b1b02af9372213563461b69ef0a65bea9033'

  for (const root of ['static-site', 'live-snapshot']) {
    for (const page of ['zh/index.html', 'zh/download.html', 'en/index.html', 'en/download.html']) {
      const html = readFileSync(join(root, page), 'utf8')
      assert.doesNotMatch(html, new RegExp(rejectedSha256))
      assert.doesNotMatch(
        html,
        /href="\/downloads\/langou-ime-android-v1\.0\.0\.apk"/,
      )
      assert.match(html, /新版验收中|New release candidate under validation/)
    }
  }
})

test('all localized pages use valid internal page and asset links', () => {
  for (const locale of ['zh', 'en'] as const) {
    for (const page of localizedPages(locale)) {
      const pagePath = join(staticRoot, locale, page)
      const html = readFileSync(pagePath, 'utf8')
      const references = [...html.matchAll(/(?:href|src)="([^"]+)"/g)].map((match) => match[1])

      for (const reference of references) {
        if (
          reference.startsWith('#') ||
          reference.startsWith('http://') ||
          reference.startsWith('https://') ||
          reference.startsWith('mailto:') ||
          reference.startsWith('/downloads/')
        ) {
          continue
        }

        const cleanReference = reference.split(/[?#]/, 1)[0]
        const candidate = reference.startsWith('/')
          ? join(staticRoot, cleanReference)
          : normalize(join(dirname(pagePath), cleanReference))
        const resolved = extname(candidate) ? candidate : join(candidate, 'index.html')

        assert.ok(existsSync(resolved), `${locale}/${page}: ${reference}`)
      }
    }
  }
})
