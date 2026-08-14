'use client'

import Image from 'next/image'
import Link from 'next/link'

import { siteCopy, type Locale } from './site-copy'

export function SiteFrame({
  locale,
  children,
}: {
  locale: Locale
  children: React.ReactNode
}) {
  const copy = siteCopy[locale]
  return (
    <main className="langou-shell">
      <div className="langou-background" />
      <header className="langou-nav">
        <Link className="langou-brand" href="/">
          <Image src="/brand/langou-app-icon-512.png" alt="Langou" width={42} height={42} />
          <span>{copy.brand}</span>
        </Link>
        <nav className="langou-nav-links">
          <Link href="/">{copy.nav.home}</Link>
          <Link href="/download">{copy.nav.download}</Link>
          <Link href="/releases">{copy.nav.releases}</Link>
          <Link href="/privacy">{copy.nav.privacy}</Link>
          <Link href="/help">{copy.nav.help}</Link>
        </nav>
      </header>
      {children}
      <footer className="site-footer">
        <span>{copy.footer}</span>
      </footer>
    </main>
  )
}

export function InnerHero({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string
  title: string
  description: string
}) {
  return (
    <section className="section inner-hero">
      <div className="glass-card section-card">
        <div className="eyebrow">{eyebrow}</div>
        <h1 className="inner-title">{title}</h1>
        <p>{description}</p>
      </div>
    </section>
  )
}
