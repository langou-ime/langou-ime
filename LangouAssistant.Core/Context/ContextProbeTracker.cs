using System.Security.Cryptography;
using System.Text;

namespace LangouAssistant.Core.Context;

public sealed record ContextObservation(
    string Application,
    string WindowTitle,
    string AccessibleConversation,
    string? Draft,
    double Confidence);

public enum ContextProbeDecision
{
    None,
    Changed,
    LowConfidenceReprobe,
}

public sealed class ContextProbeTracker
{
    private const double ReliableAccessibleTextConfidence = 0.75;
    private readonly TimeSpan _lowConfidenceProbeInterval;
    private readonly object _gate = new();
    private string? _lastObservationHash;
    private DateTimeOffset? _lastLowConfidenceProbeAt;
    private string? _lastSuggestedContentHash;

    public ContextProbeTracker(TimeSpan lowConfidenceProbeInterval)
    {
        if (lowConfidenceProbeInterval <= TimeSpan.Zero)
        {
            throw new ArgumentOutOfRangeException(nameof(lowConfidenceProbeInterval));
        }
        _lowConfidenceProbeInterval = lowConfidenceProbeInterval;
    }

    public ContextProbeDecision Decide(
        ContextObservation observation,
        DateTimeOffset now)
    {
        ArgumentNullException.ThrowIfNull(observation);
        var observationHash = Hash(
            observation.Application,
            observation.WindowTitle,
            observation.AccessibleConversation,
            observation.Draft);

        lock (_gate)
        {
            if (!string.Equals(
                    observationHash,
                    _lastObservationHash,
                    StringComparison.Ordinal))
            {
                _lastObservationHash = observationHash;
                _lastLowConfidenceProbeAt =
                    observation.Confidence < ReliableAccessibleTextConfidence
                        ? now
                        : null;
                return ContextProbeDecision.Changed;
            }

            if (observation.Confidence >= ReliableAccessibleTextConfidence)
            {
                return ContextProbeDecision.None;
            }

            if (_lastLowConfidenceProbeAt is null ||
                now - _lastLowConfidenceProbeAt >= _lowConfidenceProbeInterval)
            {
                _lastLowConfidenceProbeAt = now;
                return ContextProbeDecision.LowConfidenceReprobe;
            }

            return ContextProbeDecision.None;
        }
    }

    public bool IsNewSuggestionContent(
        string application,
        string conversation,
        string? draft)
    {
        var contentHash = Hash(application, conversation, draft);
        lock (_gate)
        {
            return !string.Equals(
                contentHash,
                _lastSuggestedContentHash,
                StringComparison.Ordinal);
        }
    }

    public void MarkSuggestionContent(
        string application,
        string conversation,
        string? draft)
    {
        var contentHash = Hash(application, conversation, draft);
        lock (_gate)
        {
            _lastSuggestedContentHash = contentHash;
        }
    }

    private static string Hash(params string?[] parts)
    {
        var value = string.Join('\n', parts);
        return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(value)));
    }
}
