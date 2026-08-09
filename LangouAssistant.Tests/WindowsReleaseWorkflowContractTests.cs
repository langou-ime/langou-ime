namespace LangouAssistant.Tests;

public sealed class WindowsReleaseWorkflowContractTests
{
    [Fact]
    public void Signed_build_requires_signpath_and_verifies_authenticode_without_publishing()
    {
        var workflow = File.ReadAllText(SourcePath());

        Assert.Contains(
            "uses: signpath/github-action-submit-signing-request@" +
            "b9d91eadd323de506c0c81cf0c7fe7438f3360fd",
            workflow,
            StringComparison.Ordinal);
        Assert.Contains("SIGNPATH_ARTIFACT_CONFIGURATION_SLUG", workflow, StringComparison.Ordinal);
        Assert.Contains("github-artifact-id:", workflow, StringComparison.Ordinal);
        Assert.Contains("Get-AuthenticodeSignature", workflow, StringComparison.Ordinal);
        Assert.Contains("$signature.Status -ne \"Valid\"", workflow, StringComparison.Ordinal);
        Assert.Contains("contents: read", workflow, StringComparison.Ordinal);
        Assert.DoesNotContain("gh release", workflow, StringComparison.Ordinal);
        Assert.DoesNotContain("contents: write", workflow, StringComparison.Ordinal);
    }

    [Fact]
    public void Public_release_never_falls_back_to_an_unsigned_msi()
    {
        var workflow = File.ReadAllText(SourcePath());

        Assert.Contains(
            "Signing and release-key settings are incomplete",
            workflow,
            StringComparison.Ordinal);
        Assert.DoesNotContain(
            "Release stays intentionally blocked",
            workflow,
            StringComparison.Ordinal);
        Assert.DoesNotContain(
            "UNSIGNED-INTERNAL",
            workflow,
            StringComparison.Ordinal);
    }

    [Fact]
    public void Workflows_use_the_fixed_public_filename_and_formal_update_key()
    {
        var release = File.ReadAllText(SourcePath());
        var ci = File.ReadAllText(CiSourcePath());

        Assert.Contains(
            "langou-ime-windows-x64-v1.0.0.msi",
            release,
            StringComparison.Ordinal);
        Assert.DoesNotContain("LangouIME-1.0.0-x64.msi", release, StringComparison.Ordinal);
        Assert.Contains(
            "RELEASE_PUBLIC_KEY_BASE64: " +
            "\"1uFuGlWZWeHpckhp2MTF6+5yCGIZYgBd5ghWEVQjx/k=\"",
            release,
            StringComparison.Ordinal);
        Assert.Contains(
            "INTERNAL_RELEASE_PUBLIC_KEY_BASE64: " +
            "\"1uFuGlWZWeHpckhp2MTF6+5yCGIZYgBd5ghWEVQjx/k=\"",
            ci,
            StringComparison.Ordinal);
    }

    [Fact]
    public void Every_third_party_action_is_pinned_to_an_official_commit()
    {
        var workflows = string.Join(
            '\n',
            File.ReadAllText(SourcePath()),
            File.ReadAllText(CiSourcePath()));

        Assert.DoesNotContain("uses: actions/checkout@v", workflows, StringComparison.Ordinal);
        Assert.DoesNotContain("uses: actions/setup-dotnet@v", workflows, StringComparison.Ordinal);
        Assert.DoesNotContain("uses: microsoft/setup-msbuild@v", workflows, StringComparison.Ordinal);
        Assert.DoesNotContain("uses: actions/cache@v", workflows, StringComparison.Ordinal);
        Assert.DoesNotContain("uses: actions/upload-artifact@v", workflows, StringComparison.Ordinal);
        Assert.DoesNotContain(
            "uses: signpath/github-action-submit-signing-request@v",
            workflows,
            StringComparison.Ordinal);
    }

    [Fact]
    public void Native_parser_build_initializes_the_visual_cpp_toolchain()
    {
        var workflows = new[]
        {
            File.ReadAllText(SourcePath()),
            File.ReadAllText(CiSourcePath()),
        };

        foreach (var workflow in workflows)
        {
            Assert.Contains("Microsoft.VisualStudio.Component.VC.Tools.x86.x64", workflow, StringComparison.Ordinal);
            Assert.Contains("VSDEVCMD=", workflow, StringComparison.Ordinal);
            Assert.Contains("call \"%VSDEVCMD%\" -arch=x64 -host_arch=x64", workflow, StringComparison.Ordinal);
        }
    }

    [Fact]
    public void Pull_request_branches_are_not_built_twice()
    {
        var workflow = File.ReadAllText(CiSourcePath());

        Assert.Contains("pull_request:", workflow, StringComparison.Ordinal);
        Assert.Contains("      - main", workflow, StringComparison.Ordinal);
        Assert.DoesNotContain("      - \"**\"", workflow, StringComparison.Ordinal);
        Assert.Contains("cancel-in-progress: true", workflow, StringComparison.Ordinal);
    }

    private static string SourcePath() =>
        Path.Combine(AppContext.BaseDirectory, "Workflows", "langou-release.yml");

    private static string CiSourcePath() =>
        Path.Combine(AppContext.BaseDirectory, "Workflows", "langou-windows-ci.yml");
}
