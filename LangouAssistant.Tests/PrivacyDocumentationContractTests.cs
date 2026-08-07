namespace LangouAssistant.Tests;

public sealed class PrivacyDocumentationContractTests
{
    [Fact]
    public void Public_policy_matches_the_product_data_flow()
    {
        var policy = File.ReadAllText(
            Path.Combine(AppContext.BaseDirectory, "Public", "PRIVACY.md"));

        Assert.Contains("30 天", policy, StringComparison.Ordinal);
        Assert.Contains("截图只存在于本机内存", policy, StringComparison.Ordinal);
        Assert.Contains("小米 MiMo API", policy, StringComparison.Ordinal);
        Assert.Contains("阿里云短信", policy, StringComparison.Ordinal);
        Assert.Contains("匿名诊断默认关闭", policy, StringComparison.Ordinal);
    }

    [Fact]
    public void Settings_window_links_to_the_owned_public_policy()
    {
        var xaml = File.ReadAllText(
            Path.Combine(AppContext.BaseDirectory, "Assistant", "SettingsWindow.xaml"));
        var codeBehind = File.ReadAllText(
            Path.Combine(AppContext.BaseDirectory, "Assistant", "SettingsWindow.xaml.cs"));

        Assert.Contains("Content=\"查看隐私政策\"", xaml, StringComparison.Ordinal);
        Assert.Contains("Click=\"PrivacyPolicy_Click\"", xaml, StringComparison.Ordinal);
        Assert.Contains(
            "https://github.com/langou-ime/windows/blob/main/PRIVACY.md",
            codeBehind,
            StringComparison.Ordinal);
    }
}
