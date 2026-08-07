using System.Net;
using System.Text;
using LangouAssistant.Core.Api;

namespace LangouAssistant.Tests;

public sealed class AccountApiTests
{
    [Fact]
    public async Task CreateGuestSession_UsesWindowsPlatformAndReturnsRotatingTokens()
    {
        HttpRequestMessage? captured = null;
        string? requestBody = null;
        var handler = new CapturingHandler(request =>
        {
            captured = request;
            requestBody = request.Content!.ReadAsStringAsync().GetAwaiter().GetResult();
            return Json("""
                {
                  "access_token":"access-1",
                  "refresh_token":"refresh-1",
                  "token_type":"bearer",
                  "expires_in":900,
                  "subject_type":"guest"
                }
                """);
        });
        var client = Client(handler);

        var result = await client.CreateGuestSessionAsync("dev-00000001", "1.0.0");

        Assert.Equal("guest", result.SubjectType);
        Assert.Equal("refresh-1", result.RefreshToken);
        Assert.Equal("/v1/devices/guest-session", captured!.RequestUri!.AbsolutePath);
        Assert.Contains("\"platform\":\"windows\"", requestBody, StringComparison.Ordinal);
    }

    [Fact]
    public async Task UpdateSettings_UsesBearerTokenAndDoesNotExposeQuota()
    {
        HttpRequestMessage? captured = null;
        string? requestBody = null;
        var handler = new CapturingHandler(request =>
        {
            captured = request;
            requestBody = request.Content!.ReadAsStringAsync().GetAwaiter().GetResult();
            return Json("""
                {"theme":"moon","auto_suggest":true,"save_history":false,"diagnostics":false}
                """);
        });
        var client = Client(handler);

        var result = await client.PutSettingsAsync(
            new ClientSettings("moon", true, false, false),
            "access-token");

        Assert.Equal("moon", result.Theme);
        Assert.Equal(HttpMethod.Put, captured!.Method);
        Assert.Equal("Bearer access-token", captured.Headers.Authorization?.ToString());
        Assert.DoesNotContain("quota", requestBody, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("limit", requestBody, StringComparison.OrdinalIgnoreCase);
    }

    private static LangouApiClient Client(HttpMessageHandler handler) => new(
        new HttpClient(handler) { BaseAddress = new Uri("https://api.langou.tech/") });

    private static HttpResponseMessage Json(string body) => new(HttpStatusCode.OK)
    {
        Content = new StringContent(body, Encoding.UTF8, "application/json"),
    };

    private sealed class CapturingHandler(Func<HttpRequestMessage, HttpResponseMessage> handler)
        : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            cancellationToken.ThrowIfCancellationRequested();
            return Task.FromResult(handler(request));
        }
    }
}
