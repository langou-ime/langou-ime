namespace LangouAssistant.Core.Privacy;

public sealed record ContextDescriptor(
    string ProcessName,
    string WindowTitle,
    bool IsPasswordField,
    bool IsSecureDesktop,
    bool IsProtectedWindow,
    string ScreenLabels = "");

public sealed record PrivacyDecision(bool AllowCapture, bool AllowTextUpload, string Reason)
{
    public static PrivacyDecision Allow() => new(true, true, "普通聊天窗口");
    public static PrivacyDecision Block(string reason) => new(false, false, reason);
}

public static class SensitiveContextPolicy
{
    private static readonly string[] BlockedProcessFragments =
    [
        "1password",
        "bitwarden",
        "keepass",
        "lastpass",
        "dashlane",
        "authenticator",
        "credentialui",
        "consent",
        "secur32",
    ];

    private static readonly string[] BlockedTitleFragments =
    [
        "密码",
        "口令",
        "验证码",
        "动态码",
        "支付",
        "付款",
        "确认付款",
        "收银台",
        "银行卡",
        "信用卡",
        "借记卡",
        "银行",
        "金融",
        "安全中心",
        "安全验证",
        "身份验证",
        "转账",
        "汇款",
        "windows security",
        "password",
        "passcode",
        "payment",
        "confirm payment",
        "checkout",
        "wallet",
        "security verification",
        "identity verification",
        "verification code",
        "one-time password",
        "enter pin",
        "bank card",
        "credit card",
        "debit card",
        "money transfer",
    ];

    public static PrivacyDecision Evaluate(ContextDescriptor context)
    {
        ArgumentNullException.ThrowIfNull(context);

        if (context.IsPasswordField)
        {
            return PrivacyDecision.Block("密码输入框禁止采集");
        }

        if (context.IsSecureDesktop)
        {
            return PrivacyDecision.Block("系统安全桌面禁止采集");
        }

        if (context.IsProtectedWindow)
        {
            return PrivacyDecision.Block("受保护窗口禁止采集");
        }

        var process = context.ProcessName.Trim().ToLowerInvariant();
        if (BlockedProcessFragments.Any(process.Contains))
        {
            return PrivacyDecision.Block("密码管理或系统安全程序禁止采集");
        }

        var visibleSecurityContext =
            $"{context.WindowTitle}\n{context.ScreenLabels}".Trim().ToLowerInvariant();
        if (BlockedTitleFragments.Any(visibleSecurityContext.Contains))
        {
            return PrivacyDecision.Block("支付、银行或安全页面禁止采集");
        }

        return PrivacyDecision.Allow();
    }
}
