using LangouAssistant.Core.Context;

namespace LangouAssistant.Tests;

public sealed class ContextProbeTrackerTests
{
    private static readonly DateTimeOffset Start =
        new(2026, 7, 29, 12, 0, 0, TimeSpan.Zero);

    [Fact]
    public void Changed_accessible_context_triggers_immediately_then_deduplicates()
    {
        var tracker = new ContextProbeTracker(TimeSpan.FromSeconds(2));
        var observation = new ContextObservation(
            "wechat",
            "项目群 - 微信",
            "小王：晚上吃什么？",
            string.Empty,
            0.92);

        Assert.Equal(ContextProbeDecision.Changed, tracker.Decide(observation, Start));
        Assert.Equal(
            ContextProbeDecision.None,
            tracker.Decide(observation, Start.AddSeconds(20)));

        Assert.Equal(
            ContextProbeDecision.Changed,
            tracker.Decide(
                observation with { AccessibleConversation = "小王：晚上吃什么？\n我：火锅？" },
                Start.AddSeconds(21)));
    }

    [Fact]
    public void Low_confidence_context_is_reprobed_at_a_bounded_interval()
    {
        var tracker = new ContextProbeTracker(TimeSpan.FromSeconds(2));
        var observation = new ContextObservation(
            "wechat",
            "微信",
            string.Empty,
            string.Empty,
            0.10);

        Assert.Equal(ContextProbeDecision.Changed, tracker.Decide(observation, Start));
        Assert.Equal(
            ContextProbeDecision.None,
            tracker.Decide(observation, Start.AddMilliseconds(1999)));
        Assert.Equal(
            ContextProbeDecision.LowConfidenceReprobe,
            tracker.Decide(observation, Start.AddSeconds(2)));
    }

    [Fact]
    public void Ocr_content_hash_suppresses_duplicate_ai_requests()
    {
        var tracker = new ContextProbeTracker(TimeSpan.FromSeconds(2));

        Assert.True(tracker.IsNewSuggestionContent("wechat", "对方：你好", ""));
        tracker.MarkSuggestionContent("wechat", "对方：你好", "");
        Assert.False(tracker.IsNewSuggestionContent("wechat", "对方：你好", ""));
        Assert.True(tracker.IsNewSuggestionContent("wechat", "对方：你好\n对方：在吗", ""));
    }
}
