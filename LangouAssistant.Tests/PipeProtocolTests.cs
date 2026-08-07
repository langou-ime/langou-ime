using LangouAssistant.Core.Protocol;

namespace LangouAssistant.Tests;

public sealed class PipeProtocolTests
{
    [Fact]
    public void ParseCommitText_AcceptsOnlyVersionedExplicitCommit()
    {
        const string json =
            """{"version":1,"type":"commit_text","request_id":"req-1","text":"好呀，晚点见～"}""";

        var command = LangouPipeProtocol.ParseCommitText(json);

        Assert.Equal(1, command.Version);
        Assert.Equal("req-1", command.RequestId);
        Assert.Equal("好呀，晚点见～", command.Text);
    }

    [Theory]
    [InlineData("""{"version":2,"type":"commit_text","request_id":"req-1","text":"ok"}""")]
    [InlineData("""{"version":1,"type":"send_text","request_id":"req-1","text":"ok"}""")]
    [InlineData("""{"version":1,"type":"commit_text","request_id":"","text":"ok"}""")]
    [InlineData("""{"version":1,"type":"commit_text","request_id":"req-1","text":""}""")]
    public void ParseCommitText_RejectsUnsafeOrUnsupportedCommands(string json)
    {
        Assert.Throws<ProtocolException>(() => LangouPipeProtocol.ParseCommitText(json));
    }

    [Fact]
    public void ParseCommitText_RejectsOversizedText()
    {
        var text = new string('懒', LangouPipeProtocol.MaxCommitTextLength + 1);
        var json =
            $$"""{"version":1,"type":"commit_text","request_id":"req-1","text":"{{text}}"}""";

        Assert.Throws<ProtocolException>(() => LangouPipeProtocol.ParseCommitText(json));
    }

    [Fact]
    public void SerializeHello_DoesNotExposeUserContent()
    {
        var json = LangouPipeProtocol.SerializeHello("1.0.0");

        Assert.Contains("\"type\":\"hello\"", json, StringComparison.Ordinal);
        Assert.Contains("\"version\":1", json, StringComparison.Ordinal);
        Assert.DoesNotContain("\"text\":", json, StringComparison.OrdinalIgnoreCase);
    }

    [Theory]
    [InlineData(
        """{"version":1,"type":"ack","request_id":"req-1","accepted":true}""",
        "req-1",
        true)]
    [InlineData(
        """{"version":1,"type":"ack","request_id":"req-2","accepted":false}""",
        "req-2",
        false)]
    public void ParseAcknowledgement_AcceptsOnlyVersionedCommitResults(
        string json,
        string requestId,
        bool accepted)
    {
        var acknowledgement = LangouPipeProtocol.ParseAcknowledgement(json);

        Assert.Equal(1, acknowledgement.Version);
        Assert.Equal(requestId, acknowledgement.RequestId);
        Assert.Equal(accepted, acknowledgement.Accepted);
    }

    [Theory]
    [InlineData(
        """{"version":2,"type":"ack","request_id":"req-1","accepted":true}""")]
    [InlineData(
        """{"version":1,"type":"hello","request_id":"req-1","accepted":true}""")]
    [InlineData(
        """{"version":1,"type":"ack","request_id":"","accepted":true}""")]
    [InlineData(
        """{"version":1,"type":"ack","request_id":"req-1","accepted":true,"text":"leak"}""")]
    public void ParseAcknowledgement_RejectsMalformedOrContentBearingMessages(string json)
    {
        Assert.Throws<ProtocolException>(
            () => LangouPipeProtocol.ParseAcknowledgement(json));
    }

    [Theory]
    [InlineData("cream")]
    [InlineData("soda")]
    [InlineData("moon")]
    public void SerializeTheme_AllowsOnlyBundledThemesAndNoUserText(string theme)
    {
        var json = LangouPipeProtocol.SerializeTheme(theme);

        Assert.Contains("\"type\":\"set_theme\"", json, StringComparison.Ordinal);
        Assert.Contains($"\"theme\":\"{theme}\"", json, StringComparison.Ordinal);
        Assert.DoesNotContain("\"text\":", json, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void SerializeTheme_RejectsUntrustedThemeNames()
    {
        Assert.Throws<ProtocolException>(
            () => LangouPipeProtocol.SerializeTheme("../../custom"));
    }
}
