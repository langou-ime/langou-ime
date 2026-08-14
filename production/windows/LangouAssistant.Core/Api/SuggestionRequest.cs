using System.Text.Json.Serialization;

namespace LangouAssistant.Core.Api;

public sealed record ConversationTurn(
    [property: JsonPropertyName("role")] string Role,
    [property: JsonPropertyName("text")] string Text);

public sealed record SuggestionRequest(
    [property: JsonPropertyName("request_id")] string RequestId,
    [property: JsonPropertyName("device_id")] string DeviceId,
    [property: JsonPropertyName("application")] string Application,
    [property: JsonPropertyName("locale")] string Locale,
    [property: JsonPropertyName("turns")] IReadOnlyList<ConversationTurn> Turns,
    [property: JsonPropertyName("draft")] string? Draft,
    [property: JsonPropertyName("save_history")] bool SaveHistory);
