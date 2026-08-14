using System.Text.Json;
using LangouAssistant.Core.Api;
using LangouAssistant.Core.Privacy;

namespace LangouAssistant.Tests;

public sealed class PrivacyPipelineTests
{
    [Fact]
    public void Redact_ReplacesCommonIdentifiersBeforeUpload()
    {
        const string input =
            "手机号 13800138000，邮箱 dog@example.com，身份证 110101199001011234，卡号 6222020202020202020";

        var redacted = TextRedactor.Redact(input);

        Assert.DoesNotContain("13800138000", redacted, StringComparison.Ordinal);
        Assert.DoesNotContain("dog@example.com", redacted, StringComparison.Ordinal);
        Assert.DoesNotContain("110101199001011234", redacted, StringComparison.Ordinal);
        Assert.DoesNotContain("6222020202020202020", redacted, StringComparison.Ordinal);
        Assert.Contains("[手机号]", redacted, StringComparison.Ordinal);
        Assert.Contains("[邮箱]", redacted, StringComparison.Ordinal);
    }

    [Fact]
    public void Segmenter_KeepsOnlyRecentNonEmptyTurns()
    {
        const string conversation = """
            小明：第一条

            我：第二条
            小明：第三条
            我：第四条
            """;

        var turns = ConversationSegmenter.Segment(conversation, 3);

        Assert.Equal(["我：第二条", "小明：第三条", "我：第四条"], turns);
    }

    [Fact]
    public void SuggestionRequestContract_CannotContainScreenshot()
    {
        var properties = typeof(SuggestionRequest).GetProperties();
        Assert.DoesNotContain(properties, property =>
            property.Name.Contains("screenshot", StringComparison.OrdinalIgnoreCase) ||
            property.PropertyType == typeof(byte[]));

        var request = new SuggestionRequest(
            "req-1",
            "guest-1",
            "wechat",
            "zh-CN",
            [new ConversationTurn("other", "吃饭了吗")],
            null,
            false);

        var json = JsonSerializer.Serialize(request);
        Assert.DoesNotContain("screenshot", json, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("image", json, StringComparison.OrdinalIgnoreCase);
    }
}
