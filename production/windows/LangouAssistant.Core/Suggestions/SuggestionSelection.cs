using LangouAssistant.Core.Protocol;

namespace LangouAssistant.Core.Suggestions;

public sealed class SuggestionSelection
{
    private IReadOnlyList<string> _suggestions = [];

    public PipeCommand? PendingCommit { get; private set; }
    public IReadOnlyList<string> Suggestions => _suggestions;

    public void Replace(IEnumerable<string> suggestions)
    {
        ArgumentNullException.ThrowIfNull(suggestions);
        _suggestions = suggestions
            .Where(suggestion => !string.IsNullOrWhiteSpace(suggestion))
            .Select(suggestion => suggestion.Trim())
            .Distinct(StringComparer.Ordinal)
            .Take(3)
            .ToArray();
        PendingCommit = null;
    }

    public PipeCommand Select(int index, string requestId)
    {
        if (index < 0 || index >= _suggestions.Count)
        {
            throw new ArgumentOutOfRangeException(nameof(index));
        }

        var command = new PipeCommand(
            LangouPipeProtocol.CurrentVersion,
            "commit_text",
            requestId,
            _suggestions[index]);
        _ = LangouPipeProtocol.Serialize(command);
        PendingCommit = command;
        return command;
    }
}
