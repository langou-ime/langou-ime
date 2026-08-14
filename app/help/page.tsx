import { InnerHero, SiteFrame } from '../components'

export default function HelpPage() {
  return (
    <SiteFrame locale="zh">
      <InnerHero
        eyebrow="Help"
        title="安装与启用帮助"
        description="把步骤压到最少，但输入法类产品仍有少量系统级启用动作。这里把 Android 和 Windows 的最短路径直接讲清楚。"
      />

      <section className="section two-column">
        <article className="glass-card section-card">
          <h2>Android 启用</h2>
          <ul>
            <li>安装 APK 后，进入系统输入法设置启用懒狗输入法</li>
            <li>按引导开启无障碍权限，获得聊天上下文识别能力</li>
            <li>如需旧系统截图能力，按一次性向导授权即可</li>
          </ul>
        </article>
        <article className="glass-card section-card">
          <h2>Windows 启用</h2>
          <ul>
            <li>双击 EXE 安装器完成安装</li>
            <li>按提示在 Windows 语言与输入设置中添加懒狗输入法</li>
            <li>首次打开可进入设置页确认 AI、历史保存和同步选项</li>
          </ul>
        </article>
      </section>
    </SiteFrame>
  )
}
