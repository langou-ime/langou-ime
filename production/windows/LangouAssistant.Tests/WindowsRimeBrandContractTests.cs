using System.Text.RegularExpressions;

namespace LangouAssistant.Tests;

public sealed partial class WindowsRimeBrandContractTests
{
    [Fact]
    public void Default_schema_list_exposes_Langou_full_pinyin_and_nine_key_only()
    {
        var config = File.ReadAllText(DataPath("default.yaml"));
        var schemaList = SchemaListPattern().Match(config);

        Assert.True(schemaList.Success, "schema_list was not found in default.yaml.");
        Assert.Equal(
            new[] { "langou_pinyin", "langou_t9" },
            SchemaEntryPattern().Matches(schemaList.Groups[1].Value)
                .Select(match => match.Groups[1].Value)
                .ToArray());
    }

    [Fact]
    public void Full_pinyin_schema_uses_the_Langou_customer_facing_name()
    {
        var schema = File.ReadAllText(DataPath("langou_pinyin.schema.yaml"));

        Assert.Contains("schema_id: langou_pinyin", schema, StringComparison.Ordinal);
        Assert.Contains("name: 懒狗全拼", schema, StringComparison.Ordinal);
        Assert.DoesNotContain("朙月拼音", schema, StringComparison.Ordinal);
    }

    [Fact]
    public void Packaged_theme_picker_exposes_exactly_the_three_Langou_skins()
    {
        var config = File.ReadAllText(DataPath("weasel.yaml"));
        var schemes = PresetColorSchemesPattern().Match(config);

        Assert.True(schemes.Success, "preset_color_schemes was not found in weasel.yaml.");
        Assert.Equal(
            new[] { "langou_cream", "langou_soda", "langou_moon" },
            ColorSchemeEntryPattern().Matches(schemes.Groups[1].Value)
                .Select(match => match.Groups[1].Value)
                .ToArray());
    }

    private static string DataPath(string fileName) =>
        Path.Combine(AppContext.BaseDirectory, "RimeData", fileName);

    [GeneratedRegex(@"(?ms)^schema_list:\s*\n(.*?)(?=^[^\s#])")]
    private static partial Regex SchemaListPattern();

    [GeneratedRegex(@"(?m)^\s+- schema:\s*([^\s#]+)")]
    private static partial Regex SchemaEntryPattern();

    [GeneratedRegex(@"(?ms)^preset_color_schemes:\s*\n(.*)\z")]
    private static partial Regex PresetColorSchemesPattern();

    [GeneratedRegex(@"(?m)^  ([a-z0-9_]+):\s*$")]
    private static partial Regex ColorSchemeEntryPattern();
}
