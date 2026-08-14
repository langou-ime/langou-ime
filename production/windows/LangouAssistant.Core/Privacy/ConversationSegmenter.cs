namespace LangouAssistant.Core.Privacy;

public static class ConversationSegmenter
{
    public static IReadOnlyList<string> Segment(string text, int maximumTurns = 12)
    {
        if (maximumTurns is < 1 or > 12)
        {
            throw new ArgumentOutOfRangeException(nameof(maximumTurns));
        }

        if (string.IsNullOrWhiteSpace(text))
        {
            return [];
        }

        return text
            .Split(
                new[] { '\r', '\n' },
                StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries
            )
            .Where(line => !string.IsNullOrWhiteSpace(line))
            .TakeLast(maximumTurns)
            .ToArray();
    }
}
