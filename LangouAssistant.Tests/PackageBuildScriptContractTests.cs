namespace LangouAssistant.Tests;

public sealed class PackageBuildScriptContractTests
{
    [Fact]
    public void Assistant_locked_restore_does_not_force_the_runtime_on_project_references()
    {
        var script = File.ReadAllText(SourcePath());
        const string restoreStart =
            "dotnet restore (Join-Path $repoRoot \"LangouAssistant\\LangouAssistant.csproj\")";
        var start = script.IndexOf(restoreStart, StringComparison.Ordinal);
        var end = script.IndexOf("if ($LASTEXITCODE", start, StringComparison.Ordinal);

        Assert.True(start >= 0 && end > start, "Assistant restore block was not found.");
        var restoreBlock = script[start..end];
        Assert.Contains("--locked-mode", restoreBlock, StringComparison.Ordinal);
        Assert.DoesNotContain("--runtime", restoreBlock, StringComparison.Ordinal);
    }

    private static string SourcePath() =>
        Path.Combine(AppContext.BaseDirectory, "Scripts", "Build-LangouPackage.ps1");
}
