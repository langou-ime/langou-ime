namespace LangouAssistant.Tests;

public sealed class NativeBrandLinkContractTests
{
    [Fact]
    public void Tray_links_to_langou_product_pages_and_keeps_rime_attribution_separate()
    {
        var source = File.ReadAllText(
            Path.Combine(AppContext.BaseDirectory, "Native", "WeaselServerApp.cpp"));

        Assert.Contains("https://langou.tech/zh/guide.html", source, StringComparison.Ordinal);
        Assert.Contains("https://langou.tech/", source, StringComparison.Ordinal);
        Assert.Contains("https://langou.tech/zh/community.html", source, StringComparison.Ordinal);
        Assert.DoesNotContain("https://rime.im/discuss/", source, StringComparison.Ordinal);
    }
}
