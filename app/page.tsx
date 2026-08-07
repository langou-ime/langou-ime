'use client'

import { useState, useEffect, createContext, useContext } from 'react'
import {
  formatReleaseBytes,
  isReleaseManifest,
  releaseManifestUrl,
  type ReleaseManifest,
  type ReleasePlatform,
} from './release-manifest'

// Simple i18n - loads translations from JSON
const I18nContext = createContext<{ t: (key: string) => string; locale: string; setLocale: (l: string) => void }>(null!)

function useI18n() { return useContext(I18nContext) }

// Translation data embedded directly
const translations: Record<string, any> = {
  zh: {
    badge: '🚀 AI驱动 · 新一代智能输入法',
    title: '懒狗输入法',
    subtitle: 'Langou Input Method',
    slogan: '解放你的双手',
    sloganEn: 'Free Your Hands',
    desc: '全球首款注入 AI Agent 的智能输入法。自动理解对话上下文，一键生成多风格回复建议。告别打字，让交流回归交流本身。',
    cta: '立即下载',
    learnMore: '了解更多',
    valuesTitle: '为什么选择懒狗输入法',
    valuesSub: '重新定义输入体验',
    values: [
      { icon: '🤖', title: 'AI驱动', text: '搭载MiMo大语言模型，深度理解对话上下文，秒级生成高质量回复建议' },
      { icon: '⚡', title: '极速体验', text: '3秒生成回复，比手动打字快10倍，一键选用即刻发送' },
      { icon: '🔒', title: '隐私安全', text: '端到端加密传输，不存储原始截图，你的对话只有你知道' }
    ],
    featuresTitle: '核心功能', featuresSub: '三步完成智能回复',
    features: [
      { num: '01', title: '智能上下文感知', text: '输入法自动识别聊天场景和对话内容，无需手动操作，后台无感运行' },
      { num: '02', title: 'AI多风格回复生成', text: '基于大模型理解对话意图，生成甜蜜、幽默、专业等不同风格回复' },
      { num: '03', title: '一键选用发送', text: '点击满意回复即可发送，无需复制粘贴，整个流程丝滑流畅' }
    ],
    platformsTitle: '支持平台', platformsSub: '多端覆盖',
    platforms: [
      { icon: '📱', name: 'Android', status: '可用', avail: true },
      { icon: '🖥️', name: 'Windows', status: '可用', avail: true },
      { icon: '🍎', name: 'iOS', status: '即将推出', avail: false },
      { icon: '💻', name: 'macOS', status: '即将推出', avail: false },
      { icon: '🔷', name: '鸿蒙', status: '即将推出', avail: false }
    ],
    dlTitle: '立即下载', dlSub: '选择你的平台',
    android: 'Android版', androidVer: 'v1.0.0-alpha', androidSize: '约45MB', androidReq: 'Android 8.0+ · 2GB+ RAM',
    windows: 'Windows版', windowsVer: 'v1.0.0-alpha', windowsSize: '约75MB', windowsReq: 'Win10/11 · x64 · 4GB+ RAM',
    download: '下载', downloadLoading: '正在读取签名发布清单…', downloadUnavailable: '正式版准备中', sha256: 'SHA-256',
    footer: '© 2026 懒狗输入法 Langou Input Method. All rights reserved.',
    tagline: '懒狗输入法，解放你的双手 🐶',
    navFeatures: '功能介绍', navPlatforms: '支持平台', navDownload: '下载',
    brand: '懒狗输入法'
  },
  en: {
    badge: '🚀 AI-Powered · Next-Gen Keyboard',
    title: 'Langou Input Method',
    subtitle: '懒狗输入法',
    slogan: 'Free Your Hands',
    sloganEn: '解放你的双手',
    desc: "The world's first AI Agent-powered keyboard. Understands chat context, generates multi-style replies. Stop typing, start living.",
    cta: 'Download Now',
    learnMore: 'Learn More',
    valuesTitle: 'Why Langou', valuesSub: 'Reinventing Input',
    values: [
      { icon: '🤖', title: 'AI-Powered', text: 'State-of-the-art MiMo LLM, deep context understanding, high-quality replies in seconds' },
      { icon: '⚡', title: 'Lightning Fast', text: '3-second generation, 10x faster than typing, one tap to send' },
      { icon: '🔒', title: 'Privacy First', text: 'End-to-end encryption, no raw screenshots stored, your chats stay private' }
    ],
    featuresTitle: 'Core Features', featuresSub: 'Smart Reply in 3 Steps',
    features: [
      { num: '01', title: 'Smart Context', text: 'Auto-detects chat scenarios and content, runs silently in the background' },
      { num: '02', title: 'Multi-Style Generation', text: 'Generates sweet, humorous, professional replies using LLM understanding' },
      { num: '03', title: 'One-Tap Send', text: 'Tap your favorite reply to send instantly, no copy-paste needed' }
    ],
    platformsTitle: 'Platforms', platformsSub: 'Multi-platform',
    platforms: [
      { icon: '📱', name: 'Android', status: 'Available', avail: true },
      { icon: '🖥️', name: 'Windows', status: 'Available', avail: true },
      { icon: '🍎', name: 'iOS', status: 'Coming Soon', avail: false },
      { icon: '💻', name: 'macOS', status: 'Coming Soon', avail: false },
      { icon: '🔷', name: 'HarmonyOS', status: 'Coming Soon', avail: false }
    ],
    dlTitle: 'Download Now', dlSub: 'Choose your platform',
    android: 'Android', androidVer: 'v1.0.0-alpha', androidSize: '~45MB', androidReq: 'Android 8.0+ · 2GB+ RAM',
    windows: 'Windows', windowsVer: 'v1.0.0-alpha', windowsSize: '~75MB', windowsReq: 'Win10/11 · x64 · 4GB+ RAM',
    download: 'Download', downloadLoading: 'Loading signed release manifest…', downloadUnavailable: 'Release pending', sha256: 'SHA-256',
    footer: '© 2026 Langou Input Method. All rights reserved.',
    tagline: 'Langou Input — Free Your Hands 🐶',
    navFeatures: 'Features', navPlatforms: 'Platforms', navDownload: 'Download',
    brand: 'Langou IME'
  }
}

export default function HomePage() {
  const [locale, setLocale] = useState('zh')

  useEffect(() => {
    const saved = localStorage.getItem('langou-locale')
    if (saved === 'en' || saved === 'zh') { setLocale(saved); return }
    if (navigator.language.startsWith('zh')) { setLocale('zh') }
    else { setLocale('en') }
  }, [])

  const switchLocale = () => {
    const next = locale === 'zh' ? 'en' : 'zh'
    setLocale(next)
    localStorage.setItem('langou-locale', next)
  }

  const t = (key: string): string => {
    const keys = key.split('.')
    let val: any = translations[locale]
    for (const k of keys) { if (val) val = val[k] }
    return val || key
  }

  const vals = (key: string): any[] => { const keys = key.split('.'); let v: any = translations[locale]; for (const k of keys) v = v?.[k]; return Array.isArray(v) ? v : [] }

  return (
    <I18nContext.Provider value={{ t, locale, setLocale: switchLocale }}>
      <div className="bg-[#0D0D1A] text-white min-h-screen overflow-x-hidden" style={{ fontFamily: locale === 'zh' ? "'Noto Sans SC','PingFang SC','Microsoft YaHei',sans-serif" : "'Inter',-apple-system,sans-serif" }}>
        <ParticleCanvas />
        <nav className="fixed top-0 left-0 right-0 z-50 flex items-center justify-between px-6 h-16 bg-[#0D0D1A]/95 backdrop-blur-xl border-b border-[#00D2FF]/15">
          <a href="#" className="text-[#00D2FF] font-bold text-lg no-underline" style={{textShadow:'0 0 20px rgba(0,210,255,0.5)'}}>🐶 {t('brand')}</a>
          <div className="hidden md:flex gap-6 items-center">
            <a href="#features" className="text-[#A0A0B0] text-sm no-underline hover:text-[#00D2FF]">{t('navFeatures')}</a>
            <a href="#platforms" className="text-[#A0A0B0] text-sm no-underline hover:text-[#00D2FF]">{t('navPlatforms')}</a>
            <a href="#download" className="text-[#A0A0B0] text-sm no-underline hover:text-[#00D2FF]">{t('navDownload')}</a>
          </div>
          <button onClick={switchLocale} className="bg-[#00D2FF]/10 border border-[#00D2FF]/30 text-[#00D2FF] px-4 py-1.5 rounded-full text-sm cursor-pointer hover:bg-[#00D2FF]/20">{locale === 'zh' ? 'EN' : '中文'}</button>
        </nav>

        {/* Hero */}
        <section className="relative z-10 min-h-screen flex items-center justify-center text-center px-6 pt-16">
          <div className="max-w-3xl">
            <div className="inline-block px-4 py-1.5 border border-[#00D2FF] rounded-full text-[#00D2FF] text-sm mb-6" style={{animation:'glowPulse 2s ease-in-out infinite'}}>{t('badge')}</div>
            <h1 className="text-5xl md:text-6xl font-extrabold mb-2 tracking-wide">
              <span className="bg-gradient-to-r from-[#00D2FF] to-[#FF007F]" style={{backgroundClip:'text',WebkitBackgroundClip:'text',WebkitTextFillColor:'transparent'}}>{t('title')}</span>
              <span className="block text-2xl md:text-3xl mt-3 opacity-60">{t('subtitle')}</span>
            </h1>
            <p className="text-2xl md:text-3xl font-light text-[#00D2FF] mb-1" style={{textShadow:'0 0 30px rgba(0,210,255,0.6)'}}>{t('slogan')}</p>
            <p className="text-lg text-[#A0A0B0] mb-2">{t('sloganEn')}</p>
            <p className="text-[#A0A0B0] mb-8 max-w-xl mx-auto leading-relaxed">{t('desc')}</p>
            <div className="flex gap-3 justify-center flex-wrap">
              <a href="#download" className="inline-block px-8 py-3.5 rounded-full font-semibold tracking-wide text-[#0D0D1A] bg-gradient-to-r from-[#00D2FF] to-[#0099CC] no-underline hover:scale-105 transition-transform" style={{boxShadow:'0 4px 30px rgba(0,210,255,0.4)'}}>{t('cta')}</a>
              <a href="#features" className="inline-block px-8 py-3.5 rounded-full font-semibold tracking-wide text-[#00D2FF] border-2 border-[#00D2FF] no-underline hover:bg-[#00D2FF]/10 transition-all">{t('learnMore')}</a>
            </div>
          </div>
        </section>

        {/* Values */}
        <section className="relative z-10 py-20 px-6 max-w-5xl mx-auto">
          <h2 className="text-3xl font-bold text-center mb-2">{t('valuesTitle')}</h2>
          <p className="text-center text-[#A0A0B0] mb-12">{t('valuesSub')}</p>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {vals('values').map((c: any, i: number) => (
              <div key={i} className="bg-[#16213E]/85 backdrop-blur-xl border border-[#00D2FF]/10 rounded-2xl p-8 text-center hover:-translate-y-2 hover:border-[#00D2FF]/40 transition-all duration-300 relative overflow-hidden group">
                <div className="absolute top-0 left-0 right-0 h-0.5 bg-gradient-to-r from-transparent via-[#00D2FF] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
                <div className="text-4xl mb-4">{c.icon}</div>
                <h3 className="text-xl font-bold text-[#00D2FF] mb-3">{c.title}</h3>
                <p className="text-[#A0A0B0] text-sm leading-relaxed">{c.text}</p>
              </div>
            ))}
          </div>
        </section>

        {/* Features */}
        <section id="features" className="relative z-10 py-20 px-6" style={{background:'linear-gradient(180deg, transparent, rgba(0,210,255,0.03), transparent)'}}>
          <div className="max-w-5xl mx-auto">
            <h2 className="text-3xl font-bold text-center mb-2">{t('featuresTitle')}</h2>
            <p className="text-center text-[#A0A0B0] mb-12">{t('featuresSub')}</p>
            <div className="space-y-16">
              {vals('features').map((item: any, i: number) => (
                <div key={i} className={`flex flex-col ${i % 2 === 0 ? 'md:flex-row' : 'md:flex-row-reverse'} items-center gap-8`}>
                  <div className="flex-1 flex justify-center">
                    <div className="w-[220px] h-[380px] bg-[#16213E]/85 backdrop-blur-xl border-2 border-[#00D2FF]/20 rounded-[30px] relative overflow-hidden" style={{boxShadow:'0 0 60px rgba(0,210,255,0.1)'}}>
                      <div className="absolute inset-[10px] bottom-[60px] bg-[#0D0D1A] rounded-[20px] flex items-center justify-center text-5xl">{item.icon || '💬'}</div>
                      <div className="absolute bottom-[60px] left-[10px] right-[10px] bg-[#16213E]/95 rounded-xl p-2 border border-[#00D2FF]">
                        <div className="text-[#00D2FF] text-[9px] mb-1.5 font-bold">🤖 AI 回复建议</div>
                        <div className="bg-[#00D2FF]/8 rounded-md p-1.5 mb-1 flex items-center gap-1 text-[9px] text-white">
                          <span className="text-[7px] px-1.5 py-0.5 bg-[#00D2FF] text-[#0D0D1A] rounded-lg font-bold">甜蜜</span>
                          宝贝我也想你！马上回来🥰
                        </div>
                        <div className="bg-[#00D2FF]/8 rounded-md p-1.5 flex items-center gap-1 text-[9px] text-white">
                          <span className="text-[7px] px-1.5 py-0.5 bg-[#00D2FF] text-[#0D0D1A] rounded-lg font-bold">温暖</span>
                          刚下班，我马上到家陪你
                        </div>
                      </div>
                    </div>
                  </div>
                  <div className="flex-1">
                    <div className="text-[#00D2FF] text-xs tracking-widest uppercase mb-1">功能 {item.num}</div>
                    <h3 className="text-2xl font-bold mb-3">{item.title}</h3>
                    <p className="text-[#A0A0B0] leading-relaxed">{item.text}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Platforms */}
        <section id="platforms" className="relative z-10 py-20 px-6 text-center">
          <div className="max-w-4xl mx-auto">
            <h2 className="text-3xl font-bold mb-2">{t('platformsTitle')}</h2>
            <p className="text-[#A0A0B0] mb-10">{t('platformsSub')}</p>
            <div className="flex justify-center gap-4 flex-wrap">
              {vals('platforms').map((p: any, i: number) => (
                <div key={i} className={`text-center p-6 rounded-2xl min-w-[120px] transition-all ${p.avail ? 'bg-[#16213E]/85 border border-[#00FF88] hover:-translate-y-1' : 'bg-[#16213E]/50 border border-white/5 opacity-50'}`} style={p.avail ? {boxShadow:'0 0 30px rgba(0,255,136,0.15)'} : {}}>
                  <div className="text-3xl mb-2">{p.icon}</div>
                  <div className="text-sm">{p.name}</div>
                  <div className={`text-xs mt-1 ${p.avail ? 'text-[#00FF88]' : 'text-[#A0A0B0]'}`}>{p.status}</div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Download */}
        <section id="download" className="relative z-10 py-20 px-6 text-center">
          <DownloadSection t={t} />
        </section>

        {/* Footer */}
        <footer className="relative z-10 py-8 px-6 border-t border-[#00D2FF]/10 text-center">
          <div className="flex justify-center gap-6 flex-wrap mb-4">
            {['隐私政策','使用条款','联系我们','GitHub'].map((link, i) => (
              <a key={i} href="#" className="text-[#A0A0B0] text-sm no-underline hover:text-[#00D2FF]">{link}</a>
            ))}
          </div>
          <p className="text-[#A0A0B0] text-xs">{t('footer')}</p>
          <p className="text-[#00D2FF] text-xs mt-2">{t('tagline')}</p>
        </footer>
      </div>
    </I18nContext.Provider>
  )
}

function DownloadSection({ t }: { t: (key: string) => string }) {
  const platforms: ReleasePlatform[] = ['android', 'windows']
  const [releases, setReleases] = useState<Partial<Record<ReleasePlatform, ReleaseManifest>>>({})
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    const abort = new AbortController()

    Promise.allSettled(
      platforms.map(async platform => {
        const response = await fetch(releaseManifestUrl(platform), {
          cache: 'no-store',
          signal: abort.signal,
        })
        if (!response.ok) throw new Error(`release manifest returned ${response.status}`)
        const candidate: unknown = await response.json()
        if (!isReleaseManifest(candidate, platform)) throw new Error('invalid release manifest')
        return candidate
      }),
    ).then(results => {
      if (abort.signal.aborted) return
      const available: Partial<Record<ReleasePlatform, ReleaseManifest>> = {}
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') available[platforms[index]] = result.value
      })
      setReleases(available)
      setLoaded(true)
    })

    return () => abort.abort()
  }, [])

  return (
    <>
      <h2 className="text-3xl font-bold mb-2">{t('dlTitle')}</h2>
      <p className="text-[#A0A0B0] mb-10">{t('dlSub')}</p>
      <div className="flex justify-center gap-6 flex-wrap">
        {platforms.map(platform => {
          const release = releases[platform]
          return (
            <div key={platform} className="bg-[#16213E]/85 backdrop-blur-xl border border-[#00D2FF]/20 rounded-2xl p-8 min-w-[280px] max-w-[360px] hover:-translate-y-1 hover:border-[#00D2FF] transition-all" style={{boxShadow:'0 0 40px rgba(0,210,255,0.08)'}}>
              <div className="text-4xl mb-3">{platform === 'android' ? '📱' : '🖥️'}</div>
              <h3 className="text-xl font-bold mb-1">{t(platform)}</h3>
              <p className="text-sm text-[#A0A0B0]">
                {release ? `v${release.version}` : loaded ? t('downloadUnavailable') : t('downloadLoading')}
              </p>
              <p className="text-xs text-[#A0A0B0] mb-4">
                {release ? formatReleaseBytes(release.size) : '—'}
              </p>
              {release ? (
                <a href={release.url} className="block w-full py-3 rounded-full font-semibold text-[#0D0D1A] bg-gradient-to-r from-[#00D2FF] to-[#0099CC] no-underline hover:scale-105 transition-transform" style={{boxShadow:'0 4px 30px rgba(0,210,255,0.4)'}}>{t('download')} {t(platform)}</a>
              ) : (
                <span aria-disabled="true" className="block w-full py-3 rounded-full font-semibold text-[#0D0D1A] bg-[#A0A0B0] cursor-not-allowed opacity-60">{t('downloadUnavailable')}</span>
              )}
              <p className="text-xs text-[#A0A0B0] mt-3">📋 {t(`${platform}Req`)}</p>
              {release && (
                <p className="text-[10px] text-[#A0A0B0] mt-3 break-all text-left">
                  {t('sha256')}: <code>{release.sha256}</code>
                </p>
              )}
            </div>
          )
        })}
      </div>
    </>
  )
}

// Particle canvas component
function ParticleCanvas() {
  useEffect(() => {
    const canvas = document.getElementById('particles') as HTMLCanvasElement
    if (!canvas) return
    const ctx = canvas.getContext('2d')!
    let animId: number
    const resize = () => { canvas.width = window.innerWidth; canvas.height = window.innerHeight }
    resize(); window.addEventListener('resize', resize)
    const particles = Array.from({ length: 80 }, () => ({
      x: Math.random() * 2000, y: Math.random() * 2000,
      s: Math.random() * 2 + 0.5, vx: (Math.random() - 0.5) * 0.3,
      vy: (Math.random() - 0.5) * 0.3, o: Math.random() * 0.5 + 0.1,
      c: Math.random() > 0.7 ? '0, 210, 255' : '255, 0, 127'
    }))
    let mx = -1000, my = -1000
    document.addEventListener('mousemove', e => { mx = e.clientX; my = e.clientY })
    const animate = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      particles.forEach(p => {
        const d = Math.hypot(mx - p.x, my - p.y)
        if (d < 200) { p.vx += (mx - p.x) * 0.0001; p.vy += (my - p.y) * 0.0001 }
        p.x += p.vx; p.y += p.vy; p.vx *= 0.995; p.vy *= 0.995
        if (p.x < -10) p.x = canvas.width + 10
        if (p.x > canvas.width + 10) p.x = -10
        if (p.y < -10) p.y = canvas.height + 10
        if (p.y > canvas.height + 10) p.y = -10
        ctx.beginPath(); ctx.arc(p.x, p.y, p.s, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(${p.c},${p.o})`; ctx.fill()
        if (p.s > 1.2) { ctx.beginPath(); ctx.arc(p.x, p.y, p.s*3, 0, Math.PI*2); ctx.fillStyle=`rgba(${p.c},${p.o*0.15})`; ctx.fill() }
      })
      for (let i=0;i<particles.length;i++) for (let j=i+1;j<particles.length;j++) {
        const d = Math.hypot(particles[i].x-particles[j].x, particles[i].y-particles[j].y)
        if (d<150) { ctx.beginPath(); ctx.moveTo(particles[i].x,particles[i].y); ctx.lineTo(particles[j].x,particles[j].y); ctx.strokeStyle=`rgba(0,210,255,${(1-d/150)*0.08})`; ctx.stroke() }
      }
      animId = requestAnimationFrame(animate)
    }
    animate()
    return () => { cancelAnimationFrame(animId); window.removeEventListener('resize',resize) }
  }, [])
  return <canvas id="particles" className="fixed inset-0 z-0 pointer-events-none" />
}
