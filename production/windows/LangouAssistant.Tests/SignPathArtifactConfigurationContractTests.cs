using System.Xml.Linq;

namespace LangouAssistant.Tests;

public sealed class SignPathArtifactConfigurationContractTests
{
    private static readonly XNamespace SignPath =
        "http://signpath.io/artifact-configuration/v1";

    [Fact]
    public void Exe_installer_and_first_party_binaries_are_deep_signed()
    {
        var document = XDocument.Load(SourcePath());
        var zip = document.Root!.Element(SignPath + "zip-file")!;
        var exe = zip.Element(SignPath + "pe-file")!;
        var includes = exe
            .Descendants(SignPath + "include")
            .Select(item => item.Attribute("path")?.Value)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        Assert.Equal(
            "langou-ime-windows-x64-v${version}.exe",
            exe.Attribute("path")?.Value);
        Assert.Contains(exe.Elements(SignPath + "authenticode-sign"), _ => true);
        Assert.Contains("LangouAssistant.exe", includes);
        Assert.Contains("LangouAssistant.dll", includes);
        Assert.Contains("LangouAssistant.Core.dll", includes);
        Assert.Contains("WeaselServer.exe", includes);
        Assert.Contains("WeaselSetup.exe", includes);
        Assert.Contains("WeaselDeployer.exe", includes);
        Assert.Contains("weasel.dll", includes);
        Assert.Contains("weaselx64.dll", includes);
        Assert.Contains("weasel.ime", includes);
        Assert.Contains("weaselx64.ime", includes);
        Assert.DoesNotContain("rime.dll", includes);
    }

    [Fact]
    public void Operator_instructions_describe_the_exe_artifact_only()
    {
        var instructions = File.ReadAllText(
            Path.Combine(AppContext.BaseDirectory, "SignPath", "README.md"));

        Assert.Contains("langou-windows-exe.xml", instructions, StringComparison.Ordinal);
        Assert.Contains("RC EXE", instructions, StringComparison.Ordinal);
        Assert.Contains("EXE 本身", instructions, StringComparison.Ordinal);
        Assert.DoesNotContain("MSI", instructions, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("langou-windows-msi.xml", instructions, StringComparison.Ordinal);
    }

    private static string SourcePath() =>
        Path.Combine(AppContext.BaseDirectory, "SignPath", "langou-windows-exe.xml");
}
