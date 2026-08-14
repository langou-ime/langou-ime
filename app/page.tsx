'use client'

import Image from 'next/image'
import { useEffect, useMemo, useState } from 'react'

import {
  formatReleaseBytes,
  isReleaseManifest,
  releaseManifestUrl,
  type ReleaseManifest,
  type ReleasePlatform,
} from './release-manifest'

type Locale = 'zh' | 'en'

type Copy = {
  brand: string
  nav: { product: string; safety: string; download: string }
  badge: string
  headline: string
  subheadline: string
  description: string
  primaryCta: string
  secondaryCta: string
  featureCards: Array<{ title: string; body: string }>
  keyboardTitle: string
  keyboardBody: string
  keyboardBullets: string[]
  aiTitle: string
  aiBody: string
  aiBullets: string[]
  safetyTitle: string
  safetyBody: string
  safetyBullets: string[]
  scenarioTitle: string
  scenarioCards: Array<{ title: string; body: string }>
  onboardingTitle: string
  onboardingSteps: Array<{ title: string; body: string }>
  comparisonTitle: string
  comparisonCards: Array<{ title: string; body: string }>
  releaseTitle: string
  releaseBody: string
  releaseBullets: string[]
  faqTitle: string
  faqs: Array<{ q: string; a: string }>
  downloadTitle: string
  downloadBody: string
  androidLabel: string
  windowsLabel: string
  androidReq: string
  windowsReq: string
  windowsSteps: string[]
  loading: string
  pending: string
  pendingWindows: string
  download: string
  sha256: string
  footer: string
}

const copy: Record<Locale, Copy> = {
  zh: {
    brand: '懒狗输入法',
    nav: { product: '产品亮点', safety: '隐私安全', download: '下载' },
    badge: '奶油懒狗 · AI 智能输入法',
    headline: '少点几下，也能回得漂亮。',
    subheadline: '像真正的输入法一样好用，也像真正的 AI 助手一样懂上下文。',
    description:
      '懒狗输入法把普通输入、9键/26键、聊天上下文理解和 AI 回复建议放进一个更顺手的体验里。默认 AI 开启，普通输入永远可用，建议只写入输入框、不自动发送。',
    primaryCta: '立即下载',
    secondaryCta: '看看功能',
    featureCards: [
      { title: '真输入法，不是聊天玩具', body: '先把 26 键、9 键、候选词、词频学习做好，再把 AI 插进真正会天天用的输入流程。' },
      { title: '默认开启 AI，但不打扰', body: '聊天上下文变化后自动出建议，不需要你先输几个字才“假装智能”。' },
      { title: '可爱一点，也成熟一点', body: '奶油白、蜜桃粉、软糖紫的懒狗视觉，面向女生也不牺牲专业感。' },
    ],
    keyboardTitle: '像你熟悉的输入法一样切换 9 键 / 26 键',
    keyboardBody:
      '交互目标不是教育用户，而是减少用户学习成本。键盘模式、候选区、功能入口都要更接近成熟输入法的直觉。',
    keyboardBullets: [
      '26 键拼音和 9 键拼音都作为正式能力存在',
      '默认路径少步骤，不把核心功能藏到多层设置里',
      '普通离线输入与 AI 建议完全解耦，断网也能正常打字',
    ],
    aiTitle: 'AI 建议应该来自聊天语境，而不是你刚打的几个字',
    aiBody:
      '懒狗输入法的核心价值是读懂聊天场景：谁在说话、前文聊到哪、现在该怎么接。建议永远只是候选，不会替用户自动发送。',
    aiBullets: [
      '优先读取聊天上下文，再决定是否触发 AI',
      '建议卡片最多三条，点击后只写入输入框',
      '敏感页面、密码框、支付/银行场景一律零采集',
    ],
    safetyTitle: '隐私和安全必须比“智能”更优先',
    safetyBody:
      '截图仅在必要时用于本地 OCR，且只驻留内存。服务端不接收截图文件，聊天历史默认 30 天并支持关闭保存或立即清空。',
    safetyBullets: [
      '密码、支付、银行、密码管理器、系统安全页不采集',
      '客户端本地 OCR，服务端只接收脱敏文本轮次',
      '官网下载、API 清单、GitHub Release 使用同一版本事实源',
    ],
    scenarioTitle: '不只是“能生成回复”，而是要在真实聊天里有黏性',
    scenarioCards: [
      { title: '暧昧聊天', body: '更像会聊天的人，而不是把对方消息机械复述一遍。' },
      { title: '朋友社交', body: '给你轻松、自然、不过火的接话方式，不需要手动切风格。' },
      { title: '工作沟通', body: '在企业微信、飞书、钉钉里给出更稳妥、更礼貌的回复建议。' },
    ],
    onboardingTitle: '上手路径尽量短，但能力尽量完整',
    onboardingSteps: [
      { title: '安装并启用输入法', body: 'Android 下载 APK 后启用；Windows 下载 EXE 后双击安装并在系统输入法设置中添加。' },
      { title: '开启关键权限', body: '只在真正需要的地方申请无障碍 / OCR 所需能力，避免用户在一开始被权限轰炸。' },
      { title: '进入真实聊天场景', body: '默认 AI 开启，但普通输入永远优先，建议卡片只做辅助、不替用户自动发送。' },
    ],
    comparisonTitle: '它不是聊天机器人，而是更成熟的输入法产品形态',
    comparisonCards: [
      { title: '普通输入可单独成立', body: '就算不看 AI，它也应该先是个能打字、能切键盘、能出候选词的输入法。' },
      { title: 'AI 不打断用户节奏', body: '建议在对的时候出现，而不是让用户为了用 AI 反过来迁就产品流程。' },
      { title: '版本入口清晰', body: '官网、API manifest、GitHub Release 使用同一个版本来源，减少下载混乱。' },
    ],
    releaseTitle: '单仓库发布，版本入口统一',
    releaseBody:
      'Android APK、Windows EXE、SHA-256 与发布说明会放在同一个 GitHub Release 下，官网与 API 清单只引用同一组正式文件。',
    releaseBullets: [
      'GitHub Release 作为公开版本入口',
      '官网下载区与 `/v1/releases/{platform}/latest` 保持一致',
      'Windows 公开包固定为一键双击安装的 EXE',
    ],
    faqTitle: '常见问题',
    faqs: [
      { q: 'AI 会不会自动替我发消息？', a: '不会。建议只会写入输入框，真正发送始终由用户自己确认。' },
      { q: '不开 AI 能不能正常当输入法用？', a: '可以。普通输入、候选词和键盘切换不会因为 AI 或网络异常而失效。' },
      { q: 'Windows 为什么要用 EXE，而不是 MSI？', a: '因为公开用户更需要低门槛双击安装体验，EXE 更符合这个目标。' },
    ],
    downloadTitle: '现在下载',
    downloadBody:
      'Android 提供 APK，Windows 提供一键双击安装的 EXE。正式发布时，官网、API 与 GitHub Releases 会同时指向同一组文件。',
    androidLabel: 'Android APK',
    windowsLabel: 'Windows EXE',
    androidReq: 'Android 8.0+ · 推荐 2GB RAM 以上',
    windowsReq: 'Windows 10/11 x64 · 双击 EXE 安装',
    windowsSteps: ['下载 EXE 安装器', '双击启动安装', '完成后到系统设置启用懒狗输入法'],
    loading: '正在读取最新发布清单…',
    pending: '正式版准备中',
    pendingWindows: 'Windows EXE 正在准备正式发布',
    download: '下载',
    sha256: 'SHA-256',
    footer: '懒狗输入法 · 少操作，强上下文，真能用的 AI 输入法',
  },
  en: {
    brand: 'Langou IME',
    nav: { product: 'Product', safety: 'Safety', download: 'Download' },
    badge: 'Creamy Langou · AI keyboard',
    headline: 'Fewer taps. Better replies.',
    subheadline: 'A real input method first, and a context-aware AI assistant second.',
    description:
      'Langou combines normal typing, 9-key / 26-key layouts, chat-context understanding, and AI reply suggestions in one smoother product. AI is on by default, normal typing always works, and suggestions never auto-send.',
    primaryCta: 'Download now',
    secondaryCta: 'See features',
    featureCards: [
      { title: 'A real IME, not a chat toy', body: 'Get pinyin layouts, candidate flow, and local learning right first; then place AI inside a keyboard people actually use every day.' },
      { title: 'AI on by default, without friction', body: 'Suggestions appear when conversation context changes, not only after the user types a few characters.' },
      { title: 'Cute, but still production-ready', body: 'Cream white, peach pink, and soft candy violet make the brand warmer without feeling childish.' },
    ],
    keyboardTitle: 'Switch 9-key / 26-key like a familiar keyboard',
    keyboardBody:
      'The product should reduce user learning cost. Keyboard modes, candidate flow, and quick actions should feel obvious from the first minute.',
    keyboardBullets: [
      '26-key pinyin and 9-key pinyin are both first-class modes',
      'Core actions stay close to the main path instead of deep settings',
      'Offline typing and AI suggestions stay decoupled so typing works without network',
    ],
    aiTitle: 'Suggestions should come from conversation context, not typed fragments',
    aiBody:
      'Langou only has value if it understands who is speaking, where the conversation is going, and what kind of reply fits now. Suggestions remain manual choices and never auto-send.',
    aiBullets: [
      'Read context first, then decide whether AI should trigger',
      'Show up to three suggestions and only write them into the input box',
      'Collect nothing in password, payment, banking, or security contexts',
    ],
    safetyTitle: 'Privacy must outrank “smartness”',
    safetyBody:
      'Screenshots are used only when necessary for local OCR and live only in memory. The server receives redacted text turns instead of screenshots, and saved history can be disabled or cleared at any time.',
    safetyBullets: [
      'Zero collection for passwords, payments, banking, password managers, and system security',
      'OCR happens locally on device; the backend only sees redacted text turns',
      'Website, API manifests, and GitHub Releases share one release source of truth',
    ],
    scenarioTitle: 'The goal is stickiness in real conversations, not demo-only AI',
    scenarioCards: [
      { title: 'Flirty chats', body: 'Replies should feel warm and socially aware instead of echoing the other person.' },
      { title: 'Casual social', body: 'Give natural follow-ups without forcing the user to manually switch styles.' },
      { title: 'Work messaging', body: 'Stay more polite and stable in WeCom, Feishu, DingTalk, and similar tools.' },
    ],
    onboardingTitle: 'Keep onboarding short while keeping the real value intact',
    onboardingSteps: [
      { title: 'Install and enable the IME', body: 'Android installs through APK. Windows installs through a double-click EXE and then gets enabled in system input settings.' },
      { title: 'Grant only the permissions that matter', body: 'Ask for accessibility / OCR-related capabilities only where they unlock the core product value.' },
      { title: 'Use it in a real conversation', body: 'AI is on by default, but normal typing always wins and suggestions never auto-send.' },
    ],
    comparisonTitle: 'This should feel like a mature IME product, not just a chat demo',
    comparisonCards: [
      { title: 'Typing stands on its own', body: 'Even without AI, the keyboard should feel complete with layouts, candidates, and normal input flow.' },
      { title: 'AI stays in the background', body: 'Suggestions should appear at the right time instead of making users learn a new ritual.' },
      { title: 'Release surface stays unified', body: 'Website, API manifests, and GitHub Releases must all point to the same public files.' },
    ],
    releaseTitle: 'One repository, one public release surface',
    releaseBody:
      'Android APK, Windows EXE, SHA-256, and release notes will live under one GitHub Release, while the website and API manifests reference the same final files.',
    releaseBullets: [
      'GitHub Release acts as the public version hub',
      'Website downloads stay aligned with `/v1/releases/{platform}/latest`',
      'The public Windows package is always a double-click EXE installer',
    ],
    faqTitle: 'FAQ',
    faqs: [
      { q: 'Will AI ever send replies automatically?', a: 'No. Suggestions only write into the input box and the user still decides whether to send.' },
      { q: 'Can I still use it as a normal keyboard with AI off?', a: 'Yes. Normal input, candidates, and layout switching do not depend on AI availability.' },
      { q: 'Why is Windows distributed as EXE instead of MSI?', a: 'Because the public product goal is a lower-friction double-click install path for normal users.' },
    ],
    downloadTitle: 'Download now',
    downloadBody:
      'Android ships as an APK. Windows ships as a double-click EXE installer. At release time, the website, API manifests, and GitHub Releases all point to the same signed files.',
    androidLabel: 'Android APK',
    windowsLabel: 'Windows EXE',
    androidReq: 'Android 8.0+ · 2GB RAM recommended',
    windowsReq: 'Windows 10/11 x64 · Double-click EXE install',
    windowsSteps: ['Download the EXE installer', 'Double-click to launch setup', 'Enable Langou IME in Windows settings after install'],
    loading: 'Loading latest release manifest…',
    pending: 'Public release pending',
    pendingWindows: 'Windows EXE is being prepared for public release',
    download: 'Download',
    sha256: 'SHA-256',
    footer: 'Langou IME · fewer steps, stronger context, AI that actually feels useful',
  },
}

export default function HomePage() {
  const [locale, setLocale] = useState<Locale>('zh')
  const text = copy[locale]

  useEffect(() => {
    const saved = localStorage.getItem('langou-locale')
    if (saved === 'zh' || saved === 'en') {
      setLocale(saved)
      return
    }
    setLocale(navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en')
  }, [])

  const toggleLocale = () => {
    const next = locale === 'zh' ? 'en' : 'zh'
    setLocale(next)
    localStorage.setItem('langou-locale', next)
  }

  return (
    <main className="langou-shell">
      <div className="langou-background" />
      <header className="langou-nav">
        <a className="langou-brand" href="#top">
          <Image src="/brand/langou-app-icon-512.png" alt="Langou" width={42} height={42} />
          <span>{text.brand}</span>
        </a>
        <nav className="langou-nav-links">
          <a href="#product">{text.nav.product}</a>
          <a href="#safety">{text.nav.safety}</a>
          <a href="#download">{text.nav.download}</a>
        </nav>
        <button className="langou-locale" type="button" onClick={toggleLocale}>
          {locale === 'zh' ? 'EN' : '中文'}
        </button>
      </header>

      <section id="top" className="hero">
        <div className="hero-copy">
          <span className="hero-badge">{text.badge}</span>
          <h1>{text.headline}</h1>
          <p className="hero-subheadline">{text.subheadline}</p>
          <p className="hero-description">{text.description}</p>
          <div className="hero-actions">
            <a className="btn btn-primary" href="#download">{text.primaryCta}</a>
            <a className="btn btn-secondary" href="#product">{text.secondaryCta}</a>
          </div>
          <div className="feature-grid">
            {text.featureCards.map(card => (
              <article key={card.title} className="glass-card feature-card">
                <h3>{card.title}</h3>
                <p>{card.body}</p>
              </article>
            ))}
          </div>
        </div>

        <div className="hero-visual">
          <div className="hero-mascot-card glass-card">
            <div className="hero-orb hero-orb-peach" />
            <div className="hero-orb hero-orb-violet" />
            <Image
              src="/brand/generated/hero-illustration-v1.png"
              alt="懒狗输入法首页主视觉"
              width={1200}
              height={1200}
              className="hero-mascot"
              priority
            />
          </div>
          <div className="keyboard-preview glass-card">
            <div className="preview-label">AI Reply</div>
            <Image
              src="/brand/langou-keyboard-easter-egg.png"
              alt="懒狗输入法键盘预览"
              width={520}
              height={300}
              className="keyboard-image"
            />
            <div className="suggestion-stack">
              <span>今晚可以呀，我七点后有空～</span>
              <span>我先忙完手头的事，晚点回你更细一点</span>
              <span>想见你，定个地方我就出门 🐶</span>
            </div>
          </div>
        </div>
      </section>

      <section id="product" className="section two-column">
        <div className="glass-card section-card">
          <div className="eyebrow">9-key / 26-key</div>
          <h2>{text.keyboardTitle}</h2>
          <p>{text.keyboardBody}</p>
          <ul>
            {text.keyboardBullets.map(item => <li key={item}>{item}</li>)}
          </ul>
        </div>
        <div className="glass-card section-card visual-card">
          <Image
            src="/brand/generated/keyboard-showcase-v2.png"
            alt="懒狗输入法 9 键与 26 键键盘展示"
            width={1600}
            height={1200}
            className="scene-image"
          />
        </div>
      </section>

      <section className="section two-column">
        <div className="glass-card section-card">
          <div className="eyebrow">Context-aware AI</div>
          <h2>{text.aiTitle}</h2>
          <p>{text.aiBody}</p>
          <ul>
            {text.aiBullets.map(item => <li key={item}>{item}</li>)}
          </ul>
        </div>
        <div className="glass-card section-card visual-card">
          <Image
            src="/brand/generated/context-chat-scene-v2.png"
            alt="懒狗输入法根据聊天语境生成建议的场景图"
            width={1600}
            height={1200}
            className="scene-image"
          />
        </div>
      </section>

      <section id="safety" className="section safety-section">
        <div className="glass-card safety-card safety-layout">
          <div>
            <div className="eyebrow">Privacy first</div>
            <h2>{text.safetyTitle}</h2>
            <p>{text.safetyBody}</p>
            <div className="pill-grid">
              {text.safetyBullets.map(item => <span key={item} className="pill">{item}</span>)}
            </div>
          </div>
          <div className="scene-visual">
            <Image
              src="/brand/generated/privacy-shield-scene-v2.png"
              alt="懒狗输入法隐私与零采集保护场景图"
              width={1200}
              height={900}
              className="scene-image"
            />
          </div>
        </div>
      </section>

      <section className="section">
        <div className="section-heading">
          <div className="eyebrow">Product preview</div>
          <h2>{locale === 'zh' ? '官网不该只有字，产品感要直接看得到' : 'The product should look visible, not text-only'}</h2>
          <p>
            {locale === 'zh'
              ? '把键盘切换、聊天建议、隐私保护和版本发布都做成一眼能看懂的视觉模块，用户不用先读完一堆说明才知道产品在干嘛。'
              : 'Keyboard modes, AI suggestions, privacy, and release flow should all be visible at a glance instead of hidden inside long copy.'}
          </p>
        </div>
        <div className="visual-story-grid">
          <article className="glass-card story-card story-card-wide">
            <Image
              src="/brand/generated/keyboard-showcase-v2.png"
              alt="懒狗输入法键盘展示主视觉"
              width={1600}
              height={1200}
              className="scene-image"
            />
            <div className="story-copy">
              <span className="pill">{locale === 'zh' ? '键盘模式' : 'Keyboard modes'}</span>
              <h3>{locale === 'zh' ? '9 键 / 26 键都放在主路径里' : 'Both 9-key and 26-key stay on the main path'}</h3>
              <p>
                {locale === 'zh'
                  ? '不再让用户为了最基本的输入切换去翻层级很深的设置。'
                  : 'Users should not dig through deep settings just to reach the basic layout they expect.'}
              </p>
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
              <span className="pill">{locale === 'zh' ? '聊天理解' : 'Context AI'}</span>
              <h3>{locale === 'zh' ? '先读语境，再给建议' : 'Read context first, then suggest'}</h3>
            </div>
          </article>
          <article className="glass-card story-card">
            <Image
              src="/brand/generated/privacy-shield-scene-v2.png"
              alt="懒狗输入法隐私安全展示图"
              width={1600}
              height={1200}
              className="scene-image"
            />
            <div className="story-copy">
              <span className="pill">{locale === 'zh' ? '隐私优先' : 'Privacy first'}</span>
              <h3>{locale === 'zh' ? '敏感页面零采集' : 'Zero collection on sensitive screens'}</h3>
            </div>
          </article>
        </div>
      </section>

      <section className="section">
        <div className="section-heading">
          <div className="eyebrow">Real chat scenarios</div>
          <h2>{text.scenarioTitle}</h2>
        </div>
        <div className="feature-grid">
          {text.scenarioCards.map(card => (
            <article key={card.title} className="glass-card feature-card">
              <h3>{card.title}</h3>
              <p>{card.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="section">
        <div className="section-heading">
          <div className="eyebrow">Onboarding</div>
          <h2>{text.onboardingTitle}</h2>
        </div>
        <div className="feature-grid">
          {text.onboardingSteps.map(step => (
            <article key={step.title} className="glass-card feature-card">
              <h3>{step.title}</h3>
              <p>{step.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="section">
        <div className="section-heading">
          <div className="eyebrow">Product shape</div>
          <h2>{text.comparisonTitle}</h2>
        </div>
        <div className="feature-grid">
          {text.comparisonCards.map(card => (
            <article key={card.title} className="glass-card feature-card">
              <h3>{card.title}</h3>
              <p>{card.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="section two-column">
        <div className="glass-card section-card">
          <div className="eyebrow">OpenClaw-style publishing</div>
          <h2>{text.releaseTitle}</h2>
          <p>{text.releaseBody}</p>
          <ul>
            {text.releaseBullets.map(item => <li key={item}>{item}</li>)}
          </ul>
        </div>
        <div className="glass-card section-card release-hub-card">
          <Image
            src="/brand/generated/release-hub-illustration-v1.png"
            alt="懒狗输入法版本发布示意"
            width={1200}
            height={900}
            className="scene-image"
          />
          <a className="btn btn-secondary release-link" href="/releases">
            {locale === 'zh' ? '查看版本发布中心' : 'Open release hub'}
          </a>
        </div>
      </section>

      <section id="download" className="section download-section">
        <div className="section-heading">
          <div className="eyebrow">Release</div>
          <h2>{text.downloadTitle}</h2>
          <p>{text.downloadBody}</p>
        </div>
        <DownloadGrid locale={locale} copy={text} />
      </section>

      <section className="section">
        <div className="section-heading">
          <div className="eyebrow">FAQ</div>
          <h2>{text.faqTitle}</h2>
        </div>
        <div className="feature-grid">
          {text.faqs.map(item => (
            <article key={item.q} className="glass-card feature-card">
              <h3>{item.q}</h3>
              <p>{item.a}</p>
            </article>
          ))}
        </div>
      </section>

      <footer className="site-footer">
        <span>{text.footer}</span>
      </footer>
    </main>
  )
}

function DownloadGrid({ locale, copy }: { locale: Locale; copy: Copy }) {
  const platforms: ReleasePlatform[] = useMemo(() => ['android', 'windows'], [])
  const [releases, setReleases] = useState<Partial<Record<ReleasePlatform, ReleaseManifest>>>({})
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    const controller = new AbortController()

    Promise.allSettled(
      platforms.map(async platform => {
        const response = await fetch(releaseManifestUrl(platform), {
          cache: 'no-store',
          signal: controller.signal,
        })
        if (!response.ok) throw new Error(`manifest ${platform} => ${response.status}`)
        const payload: unknown = await response.json()
        if (!isReleaseManifest(payload, platform)) throw new Error(`invalid ${platform} manifest`)
        return payload
      }),
    ).then(results => {
      if (controller.signal.aborted) return
      const next: Partial<Record<ReleasePlatform, ReleaseManifest>> = {}
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') next[platforms[index]] = result.value
      })
      setReleases(next)
      setLoaded(true)
    })

    return () => controller.abort()
  }, [platforms])

  return (
    <div className="download-grid">
      {platforms.map(platform => {
        const manifest = releases[platform]
        const isWindows = platform === 'windows'
        const title = isWindows ? copy.windowsLabel : copy.androidLabel
        const req = isWindows ? copy.windowsReq : copy.androidReq
        const pendingLabel = isWindows ? copy.pendingWindows : copy.pending
        const icon = isWindows ? '🪟' : '🤖'
        return (
          <article key={platform} className="glass-card download-card">
            <div className="download-card-top">
              <span className="download-icon">{icon}</span>
              <div>
                <h3>{title}</h3>
                <p>{manifest ? `v${manifest.version}` : loaded ? pendingLabel : copy.loading}</p>
              </div>
            </div>
            <div className="download-meta">
              <span>{manifest ? formatReleaseBytes(manifest.size) : req}</span>
              {isWindows && <span className="exe-badge">.exe</span>}
            </div>
            {manifest ? (
              <a className="btn btn-primary full-width" href={manifest.url}>
                {copy.download}
              </a>
            ) : (
              <span className="btn btn-disabled full-width">{copy.pending}</span>
            )}
            <p className="download-req">{req}</p>
            {isWindows && (
              <ol className="steps">
                {copy.windowsSteps.map(step => <li key={step}>{step}</li>)}
              </ol>
            )}
            {manifest && (
              <p className="hash-line">
                {copy.sha256}: <code>{manifest.sha256}</code>
              </p>
            )}
          </article>
        )
      })}
    </div>
  )
}
