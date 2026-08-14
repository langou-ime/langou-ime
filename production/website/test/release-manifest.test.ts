import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

import {
  formatReleaseBytes,
  isReleaseManifest,
  releaseManifestUrl,
} from '../app/release-manifest.ts'

const manifest = {
  platform: 'android',
  version: '1.0.0',
  minimum_supported_version: '1.0.0',
  mandatory: false,
  url: 'https://api.langou.tech/downloads/LangouIME-1.0.0.apk',
  size: 97_441_260,
  sha256: 'a'.repeat(64),
  signature: 'signed-manifest',
  published_at: '2026-07-26T00:00:00Z',
}

test('accepts the strict signed release manifest shape', () => {
  assert.equal(isReleaseManifest(manifest, 'android'), true)
  assert.equal(isReleaseManifest({ ...manifest, platform: 'windows' }, 'android'), false)
  assert.equal(isReleaseManifest({ ...manifest, url: 'http://langou.tech/file.apk' }, 'android'), false)
  assert.equal(isReleaseManifest({ ...manifest, sha256: 'bad' }, 'android'), false)
})

test('uses the production versioned release endpoint', () => {
  assert.equal(
    releaseManifestUrl('windows'),
    'https://api.langou.tech/v1/releases/windows/latest',
  )
})

test('formats binary size without pretending an estimate is exact', () => {
  assert.equal(formatReleaseBytes(97_441_260), '92.9 MiB')
})

test('website release surface keeps exe-oriented public download language', () => {
  const homepage = readFileSync('app/page.tsx', 'utf8')
  const downloadPage = readFileSync('app/download/page.tsx', 'utf8')
  const releasesPage = readFileSync('app/releases/page.tsx', 'utf8')
  const helpPage = readFileSync('app/help/page.tsx', 'utf8')
  const privacyPage = readFileSync('app/privacy/page.tsx', 'utf8')
  const styles = readFileSync('app/globals.css', 'utf8')
  const manifestHelpers = readFileSync('app/release-manifest.ts', 'utf8')

  assert.match(homepage, /Windows EXE|Double-click EXE install|双击 EXE 安装/)
  assert.match(homepage, /\.exe|Windows EXE|EXE 安装/)
  assert.doesNotMatch(homepage, /\.msi/i)
  assert.match(downloadPage, /Windows EXE|双击 EXE|EXE 安装器/)
  assert.match(releasesPage, /Release hub|版本发布中心|Windows 一键安装包/)
  assert.match(helpPage, /Windows 启用|双击 EXE 安装器/)
  assert.match(privacyPage, /本地处理优先|零采集场景/)
  assert.match(manifestHelpers, /v1\/releases\/\$\{platform\}\/latest/)
  assert.match(styles, /hero|download-grid|glass-card/)
})

test('homepage and download page render generated brand visuals instead of text-only layout', () => {
  const homepage = readFileSync('app/page.tsx', 'utf8')
  const downloadPage = readFileSync('app/download/page.tsx', 'utf8')
  const releasesPage = readFileSync('app/releases/page.tsx', 'utf8')

  assert.match(homepage, /hero-illustration-v1\.png/)
  assert.match(homepage, /keyboard-showcase-v2\.png/)
  assert.match(homepage, /context-chat-scene-v2\.png/)
  assert.match(homepage, /privacy-shield-scene-v2\.png/)
  assert.match(homepage, /release-hub-illustration-v1\.png/)
  assert.match(downloadPage, /release-hub-illustration-v1\.png/)
  assert.match(downloadPage, /keyboard-showcase-v2\.png/)
  assert.match(downloadPage, /context-chat-scene-v2\.png/)
  assert.match(downloadPage, /privacy-shield-scene-v2\.png/)
  assert.match(releasesPage, /keyboard-showcase-v2\.png/)
  assert.match(releasesPage, /context-chat-scene-v2\.png/)
  assert.match(releasesPage, /privacy-shield-scene-v2\.png/)
})

test('live snapshot download pages preserve the public windows exe release surface', () => {
  const zhIndex = readFileSync('live-snapshot/zh/index.html', 'utf8')
  const zhDownload = readFileSync('live-snapshot/zh/download.html', 'utf8')
  const enIndex = readFileSync('live-snapshot/en/index.html', 'utf8')
  const enDownload = readFileSync('live-snapshot/en/download.html', 'utf8')

  assert.match(zhIndex, /Windows 版|Windows EXE/)
  assert.match(zhDownload, /Windows EXE|下载 EXE 安装器|Windows EXE 待发布/)
  assert.doesNotMatch(zhIndex, /\.msi/i)
  assert.doesNotMatch(zhDownload, /\.msi/i)

  assert.match(enIndex, /Windows|code signing|Windows release pending/)
  assert.match(enDownload, /Windows EXE|Download the EXE installer|Windows EXE release pending/)
  assert.doesNotMatch(enIndex, /\.msi/i)
  assert.doesNotMatch(enDownload, /\.msi/i)
})
