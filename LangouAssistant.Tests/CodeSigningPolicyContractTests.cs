namespace LangouAssistant.Tests;

public sealed class CodeSigningPolicyContractTests
{
    [Fact]
    public void Repository_home_page_exposes_the_code_signing_policy()
    {
        var readme = File.ReadAllText(SourcePath("README.md"));

        Assert.Contains("## Code signing policy", readme, StringComparison.Ordinal);
        Assert.Contains("[CODE_SIGNING_POLICY.md](CODE_SIGNING_POLICY.md)", readme, StringComparison.Ordinal);
    }

    [Fact]
    public void Policy_declares_provider_roles_and_privacy_link()
    {
        var policy = File.ReadAllText(SourcePath("CODE_SIGNING_POLICY.md"));

        Assert.Contains(
            "Free code signing provided by SignPath.io, certificate by SignPath Foundation",
            policy,
            StringComparison.Ordinal);
        Assert.Contains("Committers and reviewers", policy, StringComparison.Ordinal);
        Assert.Contains("Approvers", policy, StringComparison.Ordinal);
        Assert.Contains("https://github.com/orgs/langou-ime/people", policy, StringComparison.Ordinal);
        Assert.Contains("[Privacy policy](PRIVACY.md)", policy, StringComparison.Ordinal);
    }

    [Fact]
    public void Installer_displays_the_privacy_policy_before_installation()
    {
        var installer = File.ReadAllText(OutputPath("Installer", "Package.wxs"));

        Assert.Contains("WixUILicenseRtf", installer, StringComparison.Ordinal);
        Assert.Contains("Assets\\Privacy.rtf", installer, StringComparison.Ordinal);
        var noticePath = OutputPath("Installer", "Privacy.rtf");
        Assert.True(File.Exists(noticePath), "The installer privacy notice must be packaged for tests.");
        var notice = File.ReadAllText(noticePath);
        Assert.Contains("Langou Input Method Privacy Policy", notice, StringComparison.Ordinal);
        Assert.Contains("https://langou.tech/en/privacy.html", notice, StringComparison.Ordinal);
    }

    private static string SourcePath(string fileName) =>
        Path.Combine(AppContext.BaseDirectory, "Public", fileName);

    private static string OutputPath(params string[] path) =>
        Path.Combine([AppContext.BaseDirectory, .. path]);
}
