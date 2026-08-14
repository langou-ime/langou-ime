export type ReleasePlatform = 'android' | 'windows'

export type ReleaseManifest = {
  platform: ReleasePlatform
  version: string
  minimum_supported_version: string
  mandatory: boolean
  url: string
  size: number
  sha256: string
  signature: string
  published_at: string
}

const API_ORIGIN = 'https://api.langou.tech'
const SEMVER = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?$/
const SHA256 = /^[a-f0-9]{64}$/

export function releaseManifestUrl(platform: ReleasePlatform): string {
  return `${API_ORIGIN}/v1/releases/${platform}/latest`
}

export function formatReleaseBytes(bytes: number): string {
  return `${(bytes / 1024 / 1024).toFixed(1)} MiB`
}

export function isReleaseManifest(
  value: unknown,
  platform: ReleasePlatform,
): value is ReleaseManifest {
  if (!value || typeof value !== 'object') return false
  const item = value as Record<string, unknown>

  if (
    item.platform !== platform ||
    typeof item.version !== 'string' ||
    !SEMVER.test(item.version) ||
    typeof item.minimum_supported_version !== 'string' ||
    !SEMVER.test(item.minimum_supported_version) ||
    typeof item.mandatory !== 'boolean' ||
    typeof item.size !== 'number' ||
    !Number.isSafeInteger(item.size) ||
    item.size <= 0 ||
    typeof item.sha256 !== 'string' ||
    !SHA256.test(item.sha256) ||
    typeof item.signature !== 'string' ||
    item.signature.length < 8 ||
    typeof item.published_at !== 'string' ||
    Number.isNaN(Date.parse(item.published_at)) ||
    typeof item.url !== 'string'
  ) {
    return false
  }

  try {
    return new URL(item.url).protocol === 'https:'
  } catch {
    return false
  }
}
