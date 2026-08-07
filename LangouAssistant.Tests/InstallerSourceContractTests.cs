using System.Xml.Linq;

namespace LangouAssistant.Tests;

public sealed class InstallerSourceContractTests
{
    private static readonly XNamespace Wix = "http://wixtoolset.org/schemas/v4/wxs";

    [Fact]
    public void Msi_is_machine_wide_x64_and_blocks_pre_windows_10()
    {
        var package = Load().Root!.Element(Wix + "Package")!;

        Assert.Equal("懒狗输入法", package.Attribute("Name")?.Value);
        Assert.Equal("1.0.0", package.Attribute("Version")?.Value);
        Assert.Equal("perMachine", package.Attribute("Scope")?.Value);
        Assert.Contains(
            package.Elements(Wix + "Launch"),
            item => item.Attribute("Condition")?.Value ==
                    "Installed OR (VersionNT64 >= 1000)");
    }

    [Fact]
    public void Msi_registers_tsf_and_purges_sensitive_local_state_on_uninstall()
    {
        var document = Load();
        var customActions = document.Descendants(Wix + "CustomAction").ToArray();
        var scheduled = document.Descendants(Wix + "Custom").ToArray();

        Assert.Contains(
            customActions,
            item => item.Attribute("FileRef")?.Value == "WeaselSetupExe" &&
                    item.Attribute("ExeCommand")?.Value == "/i");
        Assert.Contains(
            customActions,
            item => item.Attribute("FileRef")?.Value == "WeaselSetupExe" &&
                    item.Attribute("ExeCommand")?.Value == "/u");
        Assert.Contains(
            customActions,
            item => item.Attribute("FileRef")?.Value == "LangouAssistantExe" &&
                    item.Attribute("ExeCommand")?.Value == "/quit-and-purge" &&
                    item.Attribute("Impersonate")?.Value == "yes");
        Assert.Contains(
            scheduled,
            item => item.Attribute("Action")?.Value == "PurgeAssistantState" &&
                    item.Attribute("Condition")?.Value ==
                    "REMOVE~=\"ALL\" AND NOT UPGRADINGPRODUCTCODE");
    }

    [Fact]
    public void Msi_does_not_ship_the_retired_unsigned_updater()
    {
        var source = File.ReadAllText(SourcePath());

        Assert.DoesNotContain("WinSparkle", source, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("LangouAssistant.exe", source, StringComparison.Ordinal);
    }

    [Fact]
    public void Msi_help_link_uses_the_production_https_site()
    {
        var document = Load();
        var helpLink = document
            .Descendants(Wix + "Property")
            .Single(item => item.Attribute("Id")?.Value == "ARPHELPLINK");

        Assert.Equal("https://langou.tech/", helpLink.Attribute("Value")?.Value);
    }

    private static XDocument Load() => XDocument.Load(SourcePath());

    private static string SourcePath() =>
        Path.Combine(AppContext.BaseDirectory, "Installer", "Package.wxs");
}
