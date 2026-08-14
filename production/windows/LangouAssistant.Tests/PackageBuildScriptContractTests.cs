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

    [Fact]
    public void Packaging_script_builds_the_public_nsis_exe_and_stages_the_assistant_runtime()
    {
        var script = File.ReadAllText(SourcePath());

        Assert.Contains("output\\assistant-runtime", script, StringComparison.Ordinal);
        Assert.Contains("Assistant runtime was not staged for NSIS packaging.", script, StringComparison.Ordinal);
        Assert.Contains("makensis.exe", script, StringComparison.Ordinal);
        Assert.Contains("Get-Command \"makensis.exe\"", script, StringComparison.Ordinal);
        Assert.Contains("${env:ProgramFiles(x86)}", script, StringComparison.Ordinal);
        Assert.DoesNotContain(
            "$env:ProgramFiles(x86)\\NSIS\\Bin\\makensis.exe",
            script,
            StringComparison.Ordinal);
        Assert.Contains("output\\install.nsi", script, StringComparison.Ordinal);
        Assert.Contains(
            "Copy-Item (Join-Path $repoRoot \"LICENSE.txt\") `\n    (Join-Path $repoRoot \"output\\LICENSE.txt\")",
            script,
            StringComparison.Ordinal);
        Assert.Contains("langou-ime-windows-x64-v1.0.0.exe", script, StringComparison.Ordinal);
        Assert.DoesNotContain(".msi", script, StringComparison.OrdinalIgnoreCase);
    }

    private static string SourcePath() =>
        Path.Combine(AppContext.BaseDirectory, "Scripts", "Build-LangouPackage.ps1");
}
