using System.Net;
using System.Text;
using LangouAssistant.Core.Api;

namespace LangouAssistant.Tests;

public sealed class LangouApiClientTests
{
    [Fact]
    public async Task GetSuggestions_SendsTextOnlyAndReturnsAtMostThreeSuggestions()
    {
        string? requestBody = null;
        string? authorization = null;
        var handler = new StubHttpMessageHandler(async (request, _) =>
        {
            requestBody = await request.Content!.ReadAsStringAsync();
            authorization = request.Headers.Authorization?.ToString();
            const string sse = """
                event: meta
                data: {"request_id":"req-1","model":"mimo-v2.5-pro"}

                event: suggestion
                data: {"index":0,"style":"natural","text":"第一条"}

                event: suggestion
                data: {"index":1,"style":"gentle","text":"第二条"}

                event: suggestion
                data: {"index":2,"style":"boundary","text":"第三条"}

                event: suggestion
                data: {"index":3,"style":"extra","text":"第四条"}

                event: done
                data: {"count":3}

                """;
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(sse, Encoding.UTF8, "text/event-stream"),
            };
        });
        var client = new LangouApiClient(
            new HttpClient(handler) { BaseAddress = new Uri("https://api.langou.tech/") });

        var suggestions = await client.GetSuggestionsAsync(Request(), "access-token");

        Assert.Equal(["第一条", "第二条", "第三条"], suggestions.Select(item => item.Text));
        Assert.Equal("Bearer access-token", authorization);
        Assert.DoesNotContain("screenshot", requestBody, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("image", requestBody, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task GetSuggestions_ReportsRetryableServerError()
    {
        var handler = new StubHttpMessageHandler((_, _) =>
        {
            const string sse = """
                event: meta
                data: {"request_id":"req-1","model":"mimo-v2.5"}

                event: error
                data: {"code":"service_busy","retryable":true}

                """;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(sse, Encoding.UTF8, "text/event-stream"),
            });
        });
        var client = new LangouApiClient(
            new HttpClient(handler) { BaseAddress = new Uri("https://api.langou.tech/") });

        var exception = await Assert.ThrowsAsync<SuggestionServiceException>(
            () => client.GetSuggestionsAsync(Request(), "access-token"));

        Assert.Equal("service_busy", exception.Code);
        Assert.True(exception.Retryable);
    }

    private static SuggestionRequest Request() => new(
        "req-00000001",
        "dev-00000001",
        "wechat",
        "zh-CN",
        [new ConversationTurn("other", "周末去玩吗")],
        null,
        false);

    private sealed class StubHttpMessageHandler(
        Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> handler)
        : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken) => handler(request, cancellationToken);
    }
}
