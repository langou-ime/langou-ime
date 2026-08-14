'use client'

import Image from 'next/image'
import { useEffect, useMemo, useState } from 'react'

import { SiteFrame, InnerHero } from '../components'
import {
  formatReleaseBytes,
  isReleaseManifest,
  releaseManifestUrl,
  type ReleaseManifest,
  type ReleasePlatform,
} from '../release-manifest'

export default function DownloadPage() {
  const platforms: ReleasePlatform[] = useMemo(() => ['android', 'windows'], [])
  const [releases, setReleases] = useState<Partial<Record<ReleasePlatform, ReleaseManifest>>>({})

  useEffect(() => {
    const controller = new AbortController()
    Promise.allSettled(
      platforms.map(async platform => {
        const response = await fetch(releaseManifestUrl(platform), {
          cache: 'no-store',
          signal: controller.signal,
        })
        if (!response.ok) throw new Error(String(response.status))
        const payload: unknown = await response.json()
        if (!isReleaseManifest(payload, platform)) throw new Error('invalid manifest')
        return payload
      }),
    ).then(results => {
      if (controller.signal.aborted) return
      const next: Partial<Record<ReleasePlatform, ReleaseManifest>> = {}
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') next[platforms[index]] = result.value
      })
      setReleases(next)
    })
    return () => controller.abort()
  }, [platforms])

  return (
    <SiteFrame locale="zh">
      <InnerHero
        eyebrow="Download"
        title="下载懒狗输入法"
        description="Android 提供 APK，Windows 提供双击即装的 EXE。官网展示、API 清单与 GitHub Releases 必须指向同一组正式文件。"
      />

      <section className="section hero hero-compact">
        <div className="hero-copy">
          <span className="hero-badge">Single release surface</span>
          <h2>一个仓库、一个 Release、两端同源下载</h2>
          <p className="hero-description">
            Android APK、Windows EXE、SHA-256 和版本说明统一挂在同一个 GitHub Release 下，
            官网下载区只是更好看的正式入口，不再让用户在多个版本出口里来回找。
          </p>
        </div>
        <div className="hero-visual">
          <div className="hero-mascot-card glass-card">
            <Image
              src="/brand/generated/release-hub-illustration-v1.png"
              alt="懒狗输入法发布中心插画"
              width={1200}
              height={1200}
              className="hero-mascot"
            />
          </div>
        </div>
      </section>

      <section className="section three-column">
        <article className="glass-card story-card">
          <Image
            src="/brand/generated/keyboard-showcase-v2.png"
            alt="懒狗输入法官网键盘展示图"
            width={1600}
            height={1200}
            className="scene-image"
          />
          <div className="story-copy">
            <span className="pill">Keyboard</span>
            <h3>下载前就能看懂键盘产品感</h3>
          </div>
        </article>
        <article className="glass-card story-card">
          <Image
            src="/brand/generated/context-chat-scene-v2.png"
            alt="懒狗输入法聊天建议展示图"
            width={1600}
            height={1200}
            className="scene-image"
          />
          <div className="story-copy">
            <span className="pill">AI</span>
            <h3>建议来自聊天语境，不是假输入几字</h3>
          </div>
        </article>
        <article className="glass-card story-card">
          <Image
            src="/brand/generated/privacy-shield-scene-v2.png"
            alt="懒狗输入法隐私展示图"
            width={1600}
            height={1200}
            className="scene-image"
          />
          <div className="story-copy">
            <span className="pill">Privacy</span>
            <h3>敏感场景零采集，本地优先处理</h3>
          </div>
        </article>
      </section>

      <section className="section download-section">
        <div className="download-grid">
          {platforms.map(platform => {
            const release = releases[platform]
            const isWindows = platform === 'windows'
            return (
              <article key={platform} className="glass-card download-card">
                <div className="download-card-top">
                  <span className="download-icon">{isWindows ? '🪟' : '🤖'}</span>
                  <div>
                    <h3>{isWindows ? 'Windows EXE' : 'Android APK'}</h3>
                    <p>{release ? `v${release.version}` : '正式包准备中'}</p>
                  </div>
                </div>
                <div className="download-meta">
                  <span>{release ? formatReleaseBytes(release.size) : (isWindows ? 'Windows 10/11 x64' : 'Android 8.0+')}</span>
                  {isWindows && <span className="exe-badge">.exe</span>}
                </div>
                {release ? (
                  <a className="btn btn-primary full-width" href={release.url}>
                    立即下载
                  </a>
                ) : (
                  <span className="btn btn-disabled full-width">正式版准备中</span>
                )}
                <p className="download-req">
                  {isWindows
                    ? '安装步骤：下载 EXE → 双击安装 → 在 Windows 设置中启用懒狗输入法'
                    : '安装步骤：下载 APK → 允许安装 → 启用懒狗输入法 → 推荐开启无障碍'}
                </p>
                {release && (
                  <p className="hash-line">
                    SHA-256: <code>{release.sha256}</code>
                  </p>
                )}
              </article>
            )
          })}
        </div>
      </section>
    </SiteFrame>
  )
}
