using System.Xml.Linq;

namespace LangouAssistant.Tests;

public sealed class SignPathArtifactConfigurationContractTests
{
    private static readonly XNamespace SignPath =
        "http://signpath.io/artifact-configuration/v1";

    [Fact]
    public void Msi_and_first_party_binaries_are_deep_signed()
    {
        var document = XDocument.Load(SourcePath());
        var zip = document.Root!.Element(SignPath + "zip-file")!;
        var msi = zip.Element(SignPath + "msi-file")!;
        var includes = msi
            .Descendants(SignPath + "include")
            .Select(item => item.Attribute("path")?.Value)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        Assert.Equal(
            "langou-ime-windows-x64-v${version}.msi",
            msi.Attribute("path")?.Value);
        Assert.Contains(msi.Elements(SignPath + "authenticode-sign"), _ => true);
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

    private static string SourcePath() =>
        Path.Combine(AppContext.BaseDirectory, "SignPath", "langou-windows-msi.xml");
}
