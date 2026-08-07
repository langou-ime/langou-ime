using LangouAssistant.Core.Privacy;

namespace LangouAssistant.Tests;

public sealed class SensitiveContextPolicyTests
{
    [Theory]
    [InlineData(true, false, false, "WeChat.exe", "微信")]
    [InlineData(false, true, false, "explorer.exe", "Windows 安全中心")]
    [InlineData(false, false, true, "WeChat.exe", "微信")]
    [InlineData(false, false, false, "1Password.exe", "1Password")]
    [InlineData(false, false, false, "WeChat.exe", "银行卡支付")]
    public void Evaluate_BlocksSensitiveWindows(
        bool isPassword,
        bool isSecureDesktop,
        bool isProtectedWindow,
        string processName,
        string title)
    {
        var decision = SensitiveContextPolicy.Evaluate(
            new ContextDescriptor(processName, title, isPassword, isSecureDesktop, isProtectedWindow));

        Assert.False(decision.AllowCapture);
        Assert.False(decision.AllowTextUpload);
        Assert.NotEmpty(decision.Reason);
    }

    [Fact]
    public void Evaluate_AllowsSupportedChatWindow()
    {
        var decision = SensitiveContextPolicy.Evaluate(
            new ContextDescriptor("WeChat.exe", "项目群 - 微信", false, false, false));

        Assert.True(decision.AllowCapture);
        Assert.True(decision.AllowTextUpload);
    }

    [Theory]
    [InlineData("确认付款")]
    [InlineData("安全验证")]
    [InlineData("Confirm payment")]
    [InlineData("Enter PIN")]
    public void Evaluate_BlocksSensitiveLabelsInsideOtherwiseGenericChatWindow(string screenLabels)
    {
        var decision = SensitiveContextPolicy.Evaluate(
            new ContextDescriptor(
                "WeChat.exe",
                "微信",
                false,
                false,
                false,
                screenLabels));

        Assert.False(decision.AllowCapture);
        Assert.False(decision.AllowTextUpload);
    }
}
