using LangouAssistant.Core.Context;

namespace LangouAssistant.Tests;

public sealed class ChatApplicationMapperTests
{
    [Theory]
    [InlineData("WeChat.exe", "wechat")]
    [InlineData("QQ.exe", "qq")]
    [InlineData("WXWork.exe", "wecom")]
    [InlineData("DingTalk.exe", "dingtalk")]
    [InlineData("Feishu.exe", "feishu")]
    [InlineData("WhatsApp.exe", "whatsapp")]
    [InlineData("Telegram.exe", "telegram")]
    [InlineData("Discord.exe", "discord")]
    [InlineData("notepad.exe", "generic")]
    public void FromProcessName_MapsSupportedChatApplications(string processName, string expected)
    {
        Assert.Equal(expected, ChatApplicationMapper.FromProcessName(processName));
    }

    [Fact]
    public void IsAutomaticSuggestionTarget_ExcludesGenericEditors()
    {
        Assert.True(ChatApplicationMapper.IsAutomaticSuggestionTarget("wechat"));
        Assert.False(ChatApplicationMapper.IsAutomaticSuggestionTarget("generic"));
    }
}
