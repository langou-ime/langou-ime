export type Locale = 'zh' | 'en'

export type SiteCopy = {
  brand: string
  nav: { home: string; download: string; releases: string; privacy: string; help: string }
  footer: string
}

export const siteCopy: Record<Locale, SiteCopy> = {
  zh: {
    brand: '懒狗输入法',
    nav: {
      home: '首页',
      download: '下载',
      releases: '版本发布',
      privacy: '隐私',
      help: '帮助',
    },
    footer: '懒狗输入法 · 少操作，强上下文，真能用的 AI 输入法',
  },
  en: {
    brand: 'Langou IME',
    nav: {
      home: 'Home',
      download: 'Download',
      releases: 'Releases',
      privacy: 'Privacy',
      help: 'Help',
    },
    footer: 'Langou IME · fewer steps, stronger context, AI that actually feels useful',
  },
}
