'use client'

import Image from 'next/image'
import { useEffect, useMemo, useState } from 'react'

import { InnerHero, SiteFrame } from '../components'
import {
  formatReleaseBytes,
  isReleaseManifest,
  releaseManifestUrl,
  type ReleaseManifest,
  type ReleasePlatform,
} from '../release-manifest'

export default function ReleasesPage() {
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
        eyebrow="Release hub"
        title="版本发布中心"
        description="这里展示面向公开发布的版本入口。正式状态下，GitHub Releases、官网和 API manifest 将保持完全一致。"
      />

      <section className="section">
        <div className="glass-card section-card release-hero-card">
          <Image
            src="/brand/generated/release-hub-illustration-v1.png"
            alt="懒狗输入法版本发布中心主视觉"
            width={1600}
            height={1000}
            className="scene-image"
          />
        </div>
      </section>

      <section className="section three-column">
        <article className="glass-card story-card">
          <Image
            src="/brand/generated/keyboard-showcase-v2.png"
            alt="懒狗输入法键盘能力展示图"
            width={1600}
            height={1200}
            className="scene-image"
          />
          <div className="story-copy">
            <span className="pill">Input</span>
            <h3>核心输入能力和发布版本一起对齐</h3>
          </div>
        </article>
        <article className="glass-card story-card">
          <Image
            src="/brand/generated/context-chat-scene-v2.png"
            alt="懒狗输入法 AI 建议展示图"
            width={1600}
            height={1200}
            className="scene-image"
          />
          <div className="story-copy">
            <span className="pill">AI</span>
            <h3>官网版本介绍也直接展示产品场景</h3>
          </div>
        </article>
        <article className="glass-card story-card">
          <Image
            src="/brand/generated/privacy-shield-scene-v2.png"
            alt="懒狗输入法隐私保护展示图"
            width={1600}
            height={1200}
            className="scene-image"
          />
          <div className="story-copy">
            <span className="pill">Safety</span>
            <h3>发布页面也同步强调零采集与本地处理</h3>
          </div>
        </article>
      </section>

      <section className="section two-column">
        {platforms.map(platform => {
          const release = releases[platform]
          const isWindows = platform === 'windows'
          return (
            <article key={platform} className="glass-card section-card">
              <div className="eyebrow">{isWindows ? 'Windows EXE' : 'Android APK'}</div>
              <h2>{isWindows ? 'Windows 一键安装包' : 'Android 正式安装包'}</h2>
              <p>
                {release
                  ? `当前版本 v${release.version}，大小 ${formatReleaseBytes(release.size)}。`
                  : '正式版本尚未在公开发布入口打开。'}
              </p>
              <ul>
                <li>{isWindows ? '公开文件名：langou-ime-windows-x64-v1.0.0.exe' : '公开文件名：langou-ime-android-v1.0.0.apk'}</li>
                <li>{isWindows ? '下载方式：双击 EXE 安装' : '下载方式：APK 直装'}</li>
                <li>事实源：GitHub Release / 官网下载页 / API manifest</li>
              </ul>
              {release && (
                <>
                  <a className="btn btn-primary full-width" href={release.url}>下载当前版本</a>
                  <p className="hash-line">SHA-256: <code>{release.sha256}</code></p>
                </>
              )}
            </article>
          )
        })}
      </section>
    </SiteFrame>
  )
}
