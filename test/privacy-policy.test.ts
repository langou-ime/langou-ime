import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const approvedPrivacyPages = {
  'static-patch/zh/privacy.html': {
    liveSha256: '8071eeb7a23a13df35853b8c4be521f5c7e2803d86b8f148e060d1f7902a5307',
    required: [
      '默认保存30天',
      'AES-256-GCM',
      '关闭保存',
      '立即清空',
      '截图不会上传',
      '绝不会自动发送',
      '匿名诊断默认关闭',
    ],
    forbidden: [
      'AI生成完成后立即释放，不写入磁盘',
      '不发送个人信息',
    ],
  },
  'static-patch/en/privacy.html': {
    liveSha256: '447cf67e68731b1bab7d8eb3f5363188558d47b8288ad985f016f63866c486b0',
    required: [
      'retained for 30 days by default',
      'AES-256-GCM',
      'disable saving',
      'delete all saved history immediately',
      'Screenshots are never uploaded',
      'never sends it automatically',
      'Anonymous diagnostics are off by default',
    ],
    forbidden: [
      'server memory only',
      'we send conversation text, not personal info',
    ],
  },
} as const

test('privacy patch is restricted to the two approved live pages', () => {
  assert.deepEqual(Object.keys(approvedPrivacyPages), [
    'static-patch/zh/privacy.html',
    'static-patch/en/privacy.html',
  ])
})

for (const [path, policy] of Object.entries(approvedPrivacyPages)) {
  test(`${path} matches the v1.0.0 data lifecycle`, () => {
    const html = readFileSync(path, 'utf8')
    for (const text of policy.required) assert.match(html, new RegExp(text))
    for (const text of policy.forbidden) assert.doesNotMatch(html, new RegExp(text))
  })

  test(`${path} records the exact live-page precondition`, () => {
    const metadata = JSON.parse(
      readFileSync(`${path}.source.json`, 'utf8'),
    ) as { url?: unknown; sha256?: unknown }
    assert.equal(metadata.url, `https://langou.tech/${path.split('/').slice(1).join('/')}`)
    assert.equal(metadata.sha256, policy.liveSha256)
    assert.equal(createHash('sha256').update(policy.liveSha256).digest('hex').length, 64)
  })
}
