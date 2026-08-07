import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: '懒狗输入法 - 解放你的双手',
  description: 'AI驱动的智能输入法，自动理解对话上下文，一键生成多风格回复建议。',
  other: { 'apple-mobile-web-app-capable': 'yes' }
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700;800&family=Noto+Sans+SC:wght@300;400;500;700;900&display=swap" rel="stylesheet" />
      </head>
      <body>{children}</body>
    </html>
  )
}
