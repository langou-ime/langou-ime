using System.Text.Json.Serialization;

namespace LangouAssistant.Core.Api;

public sealed record TokenPair(
    [property: JsonPropertyName("access_token")] string AccessToken,
    [property: JsonPropertyName("refresh_token")] string RefreshToken,
    [property: JsonPropertyName("token_type")] string TokenType,
    [property: JsonPropertyName("expires_in")] int ExpiresIn,
    [property: JsonPropertyName("subject_type")] string SubjectType);

public sealed record SmsSendResponse(
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("retry_after")] int RetryAfter);

public sealed record MergeResponse(
    [property: JsonPropertyName("status")] string Status);

public sealed record ClientSettings(
    [property: JsonPropertyName("theme")] string Theme,
    [property: JsonPropertyName("auto_suggest")] bool AutoSuggest,
    [property: JsonPropertyName("save_history")] bool SaveHistory,
    [property: JsonPropertyName("diagnostics")] bool Diagnostics);
