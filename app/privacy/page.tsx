import { InnerHero, SiteFrame } from '../components'

export default function PrivacyPage() {
  return (
    <SiteFrame locale="zh">
      <InnerHero
        eyebrow="Privacy"
        title="隐私与数据说明"
        description="智能不能建立在过度采集之上。懒狗输入法默认把敏感场景排除在外，并把截图、OCR、历史保存与云端能力拆开处理。"
      />

      <section className="section two-column">
        <article className="glass-card section-card">
          <h2>本地处理优先</h2>
          <p>聊天截图仅在必要时用于本地 OCR，且只存在于内存中。客户端不会把原始截图上传到服务器或写入磁盘。</p>
          <ul>
            <li>Android 11+ 使用无障碍截图</li>
            <li>Windows 优先 UIA，失败时才走本地桌面 OCR</li>
            <li>服务端只接收脱敏后的文字轮次</li>
          </ul>
        </article>
        <article className="glass-card section-card">
          <h2>零采集场景</h2>
          <p>密码、支付、银行、密码管理器、系统安全页和受保护窗口都不会触发上下文采集或 AI 建议。</p>
          <ul>
            <li>密码框与系统安全桌面直接跳过</li>
            <li>支付/银行类界面强制禁止截图和 OCR</li>
            <li>普通输入永远可用，不因 AI 被禁用而失效</li>
          </ul>
        </article>
      </section>
    </SiteFrame>
  )
}
