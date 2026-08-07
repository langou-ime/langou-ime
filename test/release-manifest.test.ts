import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
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

test('keeps presentation files unchanged and locks reviewed build metadata', () => {
  const immutablePresentationFiles: Record<string, string> = {
    'app/globals.css': '66e04489352045c12747853fe6a3d15c2ac0991a956663031fb7c4f6c45390db',
    'app/layout.tsx': 'dfa8ffad71ae156c3b8dedf06dd25f20c8773f5a225d89b273b7dfaf3be07c95',
    'deploy/package.json': 'be6fa05ce8114d2b6f0aa83c5d70db10c635851337bf1146d16f27ecafc10895',
    'deploy/server.js': '95f8ef99360dd8eafaf26f30dc99e4e24ef843e9c5dc373b34cdb0a507a569e8',
    'messages/en.json': 'a5f7a86e44612e140f560a3853d66ce5097a33f8b9aba3ce12e5e83e8c547c3f',
    'messages/zh.json': '3dfe41638a0c81433d0980674f3d1bc27b151f62eab7316a5fac192afdcde022',
    'next.config.js': 'e350a945829c1c26d1a7ac567c868aa580f004026f6e93a4298291e4cc83ca68',
  }
  const reviewedBuildMetadata: Record<string, string> = {
    'next-env.d.ts': '7b550dda9686c16f36a17bf9051d5dbf31e98555b30d114ac49fc49a1e712651',
    'package.json': '6fb7fa66457f5ba5559b49e95ed7dccb6ea40aad7c14f634d8907d112eb4e789',
    'package-lock.json': 'fe3c725a3dd41154fe4a1988e6132eff099fd276db9ca5c1fc55aabbdd8ddab4',
    'tsconfig.json': '009e30983de28573088a1dc126f21993b8218991b9c25bdbced8e2a0bef76b7e',
  }

  for (const [path, expected] of Object.entries({
    ...immutablePresentationFiles,
    ...reviewedBuildMetadata,
  })) {
    const actual = createHash('sha256').update(readFileSync(path)).digest('hex')
    assert.equal(actual, expected, `${path} diverged from its reviewed release baseline`)
  }
})
