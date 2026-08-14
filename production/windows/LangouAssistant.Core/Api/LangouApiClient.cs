using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using LangouAssistant.Core.Update;

namespace LangouAssistant.Core.Api;

public sealed record Suggestion(string Style, string Text);

public sealed class SuggestionServiceException(
    string code,
    bool retryable,
    string? message = null)
    : Exception(message ?? code)
{
    public string Code { get; } = code;
    public bool Retryable { get; } = retryable;
}

public sealed class LangouApiClient
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    private readonly HttpClient _httpClient;

    public LangouApiClient(HttpClient httpClient)
    {
        ArgumentNullException.ThrowIfNull(httpClient);
        if (httpClient.BaseAddress is null ||
            !string.Equals(httpClient.BaseAddress.Scheme, Uri.UriSchemeHttps, StringComparison.Ordinal))
        {
            throw new ArgumentException("正式客户端 API 地址必须使用 HTTPS。", nameof(httpClient));
        }

        _httpClient = httpClient;
    }

    public async Task<IReadOnlyList<Suggestion>> GetSuggestionsAsync(
        SuggestionRequest request,
        string accessToken,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);
        if (string.IsNullOrWhiteSpace(accessToken))
        {
            throw new ArgumentException("访问令牌不能为空。", nameof(accessToken));
        }

        using var message = new HttpRequestMessage(HttpMethod.Post, "v1/ai/suggestions:stream")
        {
            Content = JsonContent.Create(request, options: JsonOptions),
        };
        message.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        message.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("text/event-stream"));

        using var response = await _httpClient.SendAsync(
            message,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);
        response.EnsureSuccessStatusCode();

        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var reader = new StreamReader(stream, Encoding.UTF8, detectEncodingFromByteOrderMarks: false);
        var suggestions = new List<Suggestion>(3);
        string? eventName = null;
        var data = new StringBuilder();

        while (await reader.ReadLineAsync(cancellationToken) is { } line)
        {
            if (line.Length == 0)
            {
                ProcessEvent(eventName, data.ToString(), suggestions);
                eventName = null;
                data.Clear();
                continue;
            }

            if (line.StartsWith("event:", StringComparison.Ordinal))
            {
                eventName = line[6..].Trim();
            }
            else if (line.StartsWith("data:", StringComparison.Ordinal))
            {
                if (data.Length > 0)
                {
                    data.Append('\n');
                }
                data.Append(line[5..].TrimStart());
            }
        }

        ProcessEvent(eventName, data.ToString(), suggestions);
        return suggestions;
    }

    public async Task<TokenPair> CreateGuestSessionAsync(
        string deviceId,
        string appVersion,
        CancellationToken cancellationToken = default) =>
        await PostJsonAsync<TokenPair>(
            "v1/devices/guest-session",
            new { device_id = deviceId, platform = "windows", app_version = appVersion },
            null,
            cancellationToken);

    public async Task<TokenPair> RefreshTokenAsync(
        string refreshToken,
        CancellationToken cancellationToken = default) =>
        await PostJsonAsync<TokenPair>(
            "v1/auth/token/refresh",
            new { refresh_token = refreshToken },
            null,
            cancellationToken);

    public async Task<SmsSendResponse> SendSmsAsync(
        string phone,
        CancellationToken cancellationToken = default) =>
        await PostJsonAsync<SmsSendResponse>(
            "v1/auth/sms/send",
            new { phone },
            null,
            cancellationToken);

    public async Task<TokenPair> VerifySmsAsync(
        string phone,
        string code,
        string deviceId,
        CancellationToken cancellationToken = default) =>
        await PostJsonAsync<TokenPair>(
            "v1/auth/sms/verify",
            new { phone, code, device_id = deviceId },
            null,
            cancellationToken);

    public async Task<MergeResponse> MergeGuestAsync(
        string guestRefreshToken,
        string userAccessToken,
        CancellationToken cancellationToken = default) =>
        await PostJsonAsync<MergeResponse>(
            "v1/auth/sms/merge",
            new { guest_refresh_token = guestRefreshToken },
            userAccessToken,
            cancellationToken);

    public async Task<ClientSettings> GetSettingsAsync(
        string accessToken,
        CancellationToken cancellationToken = default)
    {
        using var request = Authorized(HttpMethod.Get, "v1/settings", accessToken);
        return await SendAsync<ClientSettings>(request, cancellationToken);
    }

    public async Task<ClientSettings> PutSettingsAsync(
        ClientSettings settings,
        string accessToken,
        CancellationToken cancellationToken = default)
    {
        using var request = Authorized(HttpMethod.Put, "v1/settings", accessToken);
        request.Content = JsonContent.Create(settings, options: JsonOptions);
        return await SendAsync<ClientSettings>(request, cancellationToken);
    }

    public async Task DeleteHistoryAsync(
        string accessToken,
        string? historyId = null,
        CancellationToken cancellationToken = default)
    {
        var path = historyId is null
            ? "v1/history"
            : $"v1/history?id={Uri.EscapeDataString(historyId)}";
        using var request = Authorized(HttpMethod.Delete, path, accessToken);
        using var response = await _httpClient.SendAsync(request, cancellationToken);
        response.EnsureSuccessStatusCode();
    }

    public async Task<ReleaseManifest> GetLatestWindowsReleaseAsync(
        CancellationToken cancellationToken = default)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "v1/releases/windows/latest");
        return await SendAsync<ReleaseManifest>(request, cancellationToken);
    }

    private static void ProcessEvent(
        string? eventName,
        string data,
        List<Suggestion> suggestions)
    {
        if (string.IsNullOrEmpty(eventName) || string.IsNullOrEmpty(data))
        {
            return;
        }

        if (string.Equals(eventName, "suggestion", StringComparison.Ordinal) &&
            suggestions.Count < 3)
        {
            var payload = JsonSerializer.Deserialize<SuggestionEvent>(data, JsonOptions)
                ?? throw new SuggestionServiceException("invalid_response", true);
            if (!string.IsNullOrWhiteSpace(payload.Text))
            {
                suggestions.Add(new Suggestion(payload.Style, payload.Text.Trim()));
            }
        }
        else if (string.Equals(eventName, "error", StringComparison.Ordinal))
        {
            var payload = JsonSerializer.Deserialize<ErrorEvent>(data, JsonOptions)
                ?? throw new SuggestionServiceException("invalid_response", true);
            throw new SuggestionServiceException(payload.Code, payload.Retryable);
        }
    }

    private async Task<T> PostJsonAsync<T>(
        string path,
        object body,
        string? accessToken,
        CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, path)
        {
            Content = JsonContent.Create(body, options: JsonOptions),
        };
        if (accessToken is not null)
        {
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        }
        return await SendAsync<T>(request, cancellationToken);
    }

    private async Task<T> SendAsync<T>(
        HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        using var response = await _httpClient.SendAsync(request, cancellationToken);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<T>(JsonOptions, cancellationToken)
            ?? throw new HttpRequestException("API 返回了空响应。");
    }

    private static HttpRequestMessage Authorized(
        HttpMethod method,
        string path,
        string accessToken)
    {
        if (string.IsNullOrWhiteSpace(accessToken))
        {
            throw new ArgumentException("访问令牌不能为空。", nameof(accessToken));
        }

        var request = new HttpRequestMessage(method, path);
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        return request;
    }

    private sealed record SuggestionEvent(
        [property: JsonPropertyName("index")] int Index,
        [property: JsonPropertyName("style")] string Style,
        [property: JsonPropertyName("text")] string Text);

    private sealed record ErrorEvent(
        [property: JsonPropertyName("code")] string Code,
        [property: JsonPropertyName("retryable")] bool Retryable);
}
