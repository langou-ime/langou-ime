using LangouAssistant.Core.Protocol;
using LangouAssistant.Core.Suggestions;

namespace LangouAssistant.Tests;

public sealed class SuggestionSelectionTests
{
    [Fact]
    public void ReceivingSuggestions_NeverCreatesACommitCommand()
    {
        var selection = new SuggestionSelection();

        selection.Replace(["第一条", "第二条", "第三条"]);

        Assert.Null(selection.PendingCommit);
    }

    [Fact]
    public void UserSelection_CreatesCommitTextButNeverSendText()
    {
        var selection = new SuggestionSelection();
        selection.Replace(["可以呀～", "我晚点回你", "收到啦"]);

        var command = selection.Select(0, "req-42");

        Assert.Equal("commit_text", command.Type);
        Assert.Equal("可以呀～", command.Text);
        Assert.DoesNotContain("send", LangouPipeProtocol.Serialize(command), StringComparison.OrdinalIgnoreCase);
    }
}
