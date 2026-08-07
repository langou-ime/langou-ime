import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const workflow = readFileSync('.github/workflows/ci.yml', 'utf8')

test('website CI has minimum permissions and verifies the production build', () => {
  assert.match(workflow, /permissions:\n  contents: read/)
  assert.match(workflow, /npm ci/)
  assert.match(workflow, /npm test/)
  assert.match(workflow, /npm run build/)
})

test('website CI pins every action to an immutable commit', () => {
  const actionLines = workflow
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.startsWith('uses:'))

  assert.ok(actionLines.length > 0)
  for (const line of actionLines) {
    const ref = line.split('@', 2)[1].split(/\s/, 1)[0]
    assert.match(ref, /^[0-9a-f]{40}$/)
  }
})
