using LangouAssistant.Core.Update;

namespace LangouAssistant.Tests;

public sealed class ReleaseSecurityTests
{
    [Fact]
    public void Verify_MatchesFastApiCanonicalSignatureAndRejectsTampering()
    {
        const string publicKey =
            "11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo=";
        var manifest = new ReleaseManifest(
            "windows",
            "1.2.3",
            "1.0.0",
            false,
            "https://download.langou.tech/langou.msi",
            42_000_000,
            new string('a', 64),
            "yzFBH7h0Wr1bmL9jlgfrnKh4siXMIycbmmccgpl4xJblnBcj5xyP1osQsFxO+EbYuk6Lnf5yd2Yg95EeA9kcDg==",
            "2026-07-26T12:00:00Z");

        Assert.True(ReleaseSignatureVerifier.Verify(manifest, publicKey));
        Assert.False(ReleaseSignatureVerifier.Verify(manifest with { Size = 42_000_001 }, publicKey));
    }

    [Theory]
    [InlineData("1.0.0", "1.0.0", "1.0.0", false, ReleaseDecision.Current)]
    [InlineData("1.0.0", "1.1.0", "1.0.0", false, ReleaseDecision.Optional)]
    [InlineData("1.0.0", "2.0.0", "1.5.0", false, ReleaseDecision.Mandatory)]
    [InlineData("1.0.0", "1.1.0", "1.0.0", true, ReleaseDecision.Mandatory)]
    public void Evaluate_ClassifiesSignedWindowsReleases(
        string current,
        string latest,
        string minimum,
        bool mandatory,
        ReleaseDecision expected)
    {
        var manifest = new ReleaseManifest(
            "windows",
            latest,
            minimum,
            mandatory,
            "https://download.langou.tech/langou.msi",
            42,
            new string('a', 64),
            "signature",
            "2026-07-26T12:00:00Z");

        Assert.Equal(expected, ReleaseUpdatePolicy.Evaluate(current, manifest));
    }
}
