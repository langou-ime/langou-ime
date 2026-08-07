using System.Reflection;
using System.Runtime.InteropServices;
using System.Windows;
using LangouAssistant.Core.Api;
using LangouAssistant.Core.Context;
using LangouAssistant.Core.Privacy;
using LangouAssistant.Core.Suggestions;
using LangouAssistant.Core.Storage;
using LangouAssistant.Core.Update;

namespace LangouAssistant.Services;

public sealed record ReleaseCheckResult(
    ReleaseDecision Decision,
    string Message,
    Uri? DownloadUri);

public sealed record SettingsSaveResult(
    ClientSettings Settings,
    bool CloudSynced);

public sealed class AssistantCoordinator : IDisposable
{
    private readonly MainWindow _window;
    private readonly LangouApiClient _api;
    private readonly SessionManager _sessions;
    private readonly LangouPipeServer _pipe;
    private readonly UiAutomationContextReader _contextReader;
    private readonly DesktopCaptureService _capture;
    private readonly PaddleOcrService _ocr;
    private readonly ClientSettingsLocalStore _localSettings;
    private readonly SuggestionSelection _selection = new();
    private readonly ContextProbeTracker _contextProbe =
        new(TimeSpan.FromSeconds(2));
    private readonly CancellationTokenSource _shutdown = new();
    private CancellationTokenSource? _debounce;
    private Task? _monitorLoop;
    private Task? _generationTask;
    private string? _activeRequestId;
    private ClientSettings _settings = new("cream", true, true, false);

    public AssistantCoordinator(
        MainWindow window,
        LangouApiClient api,
        SessionManager sessions,
        LangouPipeServer pipe,
        UiAutomationContextReader contextReader,
        DesktopCaptureService capture,
        PaddleOcrService ocr,
        ClientSettingsLocalStore localSettings)
    {
        _window = window;
        _api = api;
        _sessions = sessions;
        _pipe = pipe;
        _contextReader = contextReader;
        _capture = capture;
        _ocr = ocr;
        _localSettings = localSettings;
        _settings = _localSettings.Load() ?? _settings;
    }

    public ClientSettings Settings => _settings;
    public string SubjectType => _sessions.SubjectType;

    public async Task StartAsync()
    {
        _pipe.Start();
        try
        {
            var token = await _sessions.GetAccessTokenAsync(_shutdown.Token);
            _settings = await _api.GetSettingsAsync(token, _shutdown.Token);
            _localSettings.Save(_settings);
        }
        catch (Exception exception) when (
            exception is HttpRequestException or TaskCanceledException)
        {
            // Offline typing and the tray remain available.
        }
        await SendThemeWhenConnectedAsync(_settings.Theme, _shutdown.Token);
        _monitorLoop = MonitorAsync(_shutdown.Token);
        await Task.CompletedTask;
    }

    private async Task MonitorAsync(CancellationToken cancellationToken)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(450));
        while (await timer.WaitForNextTickAsync(cancellationToken))
        {
            ContextSnapshot? snapshot;
            try
            {
                snapshot = await _contextReader.ReadAsync(cancellationToken);
            }
            catch
            {
                _window.ShowUnavailable();
                continue;
            }

            if (snapshot is null ||
                !_settings.AutoSuggest ||
                !snapshot.Privacy.AllowCapture ||
                !ChatApplicationMapper.IsAutomaticSuggestionTarget(snapshot.Application))
            {
                _debounce?.Cancel();
                _window.ShowUnavailable();
                continue;
            }

            var probeDecision = _contextProbe.Decide(
                new ContextObservation(
                    snapshot.Application,
                    snapshot.WindowTitle,
                    snapshot.AccessibleConversation,
                    snapshot.Draft,
                    snapshot.Confidence),
                DateTimeOffset.UtcNow);
            if (probeDecision == ContextProbeDecision.None)
            {
                continue;
            }

            if (probeDecision == ContextProbeDecision.LowConfidenceReprobe &&
                _generationTask is { IsCompleted: false })
            {
                continue;
            }

            _debounce?.Cancel();
            _debounce?.Dispose();
            _debounce = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            _generationTask = GenerateAfterDebounceAsync(snapshot, _debounce.Token);
        }
    }

    private async Task GenerateAfterDebounceAsync(
        ContextSnapshot snapshot,
        CancellationToken cancellationToken)
    {
        try
        {
            await Task.Delay(TimeSpan.FromMilliseconds(700), cancellationToken);
            var conversation = snapshot.AccessibleConversation;
            if (snapshot.Confidence < 0.75)
            {
                using var screenshot = _capture.CaptureWindow(snapshot.ForegroundWindow);
                if (screenshot is not null)
                {
                    conversation = await _ocr.RecognizeAsync(screenshot, cancellationToken);
                }
            }

            var turns = BuildTurns(conversation);
            if (turns.Count == 0)
            {
                _window.ShowUnavailable();
                return;
            }
            if (!_contextProbe.IsNewSuggestionContent(
                    snapshot.Application,
                    conversation,
                    snapshot.Draft))
            {
                return;
            }

            var (left, top) = OverlayPosition(snapshot.Anchor);
            _window.ShowLoading(left, top);
            var requestId = $"req_{Guid.NewGuid():N}";
            var token = await _sessions.GetAccessTokenAsync(cancellationToken);
            var request = new SuggestionRequest(
                requestId,
                _sessions.DeviceId,
                snapshot.Application,
                "zh-CN",
                turns,
                string.IsNullOrWhiteSpace(snapshot.Draft)
                    ? null
                    : TextRedactor.Redact(snapshot.Draft).Trim(),
                _settings.SaveHistory);
            var suggestions = await _api.GetSuggestionsAsync(request, token, cancellationToken);
            _contextProbe.MarkSuggestionContent(
                snapshot.Application,
                conversation,
                snapshot.Draft);
            _selection.Replace(suggestions.Select(item => item.Text));
            _activeRequestId = requestId;
            _window.ShowSuggestions(suggestions, left, top);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            // A newer context superseded this request.
        }
        catch (Exception exception) when (
            exception is HttpRequestException or SuggestionServiceException or
            FileNotFoundException or ExternalException)
        {
            _window.ShowUnavailable();
        }
    }

    public async Task<bool> CommitSuggestionAsync(
        int index,
        CancellationToken cancellationToken = default)
    {
        if (_activeRequestId is null)
        {
            return false;
        }

        try
        {
            var command = _selection.Select(index, _activeRequestId);
            return await _pipe.SendCommitAsync(command, cancellationToken);
        }
        catch (ArgumentOutOfRangeException)
        {
            return false;
        }
    }

    public Task<SmsSendResponse> SendSmsAsync(
        string phone,
        CancellationToken cancellationToken = default) =>
        _sessions.SendSmsAsync(phone, cancellationToken);

    public async Task VerifySmsAsync(
        string phone,
        string code,
        CancellationToken cancellationToken = default)
    {
        await _sessions.VerifySmsAsync(phone, code, cancellationToken);
        var token = await _sessions.GetAccessTokenAsync(cancellationToken);
        _settings = await _api.GetSettingsAsync(token, cancellationToken);
        _localSettings.Save(_settings);
    }

    public async Task<SettingsSaveResult> SaveSettingsAsync(
        ClientSettings settings,
        CancellationToken cancellationToken = default)
    {
        _localSettings.Save(settings);
        _settings = settings;
        await SendThemeWhenConnectedAsync(_settings.Theme, cancellationToken);
        if (!_settings.AutoSuggest)
        {
            _window.ShowUnavailable();
        }

        try
        {
            var token = await _sessions.GetAccessTokenAsync(cancellationToken);
            _settings = await _api.PutSettingsAsync(settings, token, cancellationToken);
            _localSettings.Save(_settings);
            return new SettingsSaveResult(_settings, true);
        }
        catch (Exception exception) when (
            exception is HttpRequestException or TaskCanceledException)
        {
            return new SettingsSaveResult(_settings, false);
        }
    }

    public async Task ClearHistoryAsync(CancellationToken cancellationToken = default)
    {
        var token = await _sessions.GetAccessTokenAsync(cancellationToken);
        await _api.DeleteHistoryAsync(token, cancellationToken: cancellationToken);
    }

    public async Task<ReleaseCheckResult> CheckForUpdateAsync(
        CancellationToken cancellationToken = default)
    {
        var manifest = await _api.GetLatestWindowsReleaseAsync(cancellationToken);
        var publicKey = Assembly
            .GetExecutingAssembly()
            .GetCustomAttributes<AssemblyMetadataAttribute>()
            .FirstOrDefault(attribute =>
                string.Equals(attribute.Key, "LangouReleasePublicKey", StringComparison.Ordinal))
            ?.Value;
        if (string.IsNullOrWhiteSpace(publicKey) ||
            !ReleaseSignatureVerifier.Verify(manifest, publicKey))
        {
            return new ReleaseCheckResult(
                ReleaseDecision.Rejected,
                "更新清单签名无效，已阻止下载。",
                null);
        }

        var decision = ReleaseUpdatePolicy.Evaluate("1.0.0", manifest);
        return decision switch
        {
            ReleaseDecision.Current =>
                new ReleaseCheckResult(decision, "已经是最新版啦。", null),
            ReleaseDecision.Optional =>
                new ReleaseCheckResult(decision, $"发现 v{manifest.Version}，可以更新。", new Uri(manifest.Url)),
            ReleaseDecision.Mandatory =>
                new ReleaseCheckResult(decision, $"v{manifest.Version} 是安全必需更新。", new Uri(manifest.Url)),
            _ =>
                new ReleaseCheckResult(decision, "更新清单格式不受支持。", null),
        };
    }

    public void SignOut()
    {
        _sessions.SignOut();
        _settings = new ClientSettings("cream", true, true, false);
        _localSettings.Save(_settings);
    }

    private static IReadOnlyList<ConversationTurn> BuildTurns(string text)
    {
        var sanitized = TextRedactor.Redact(text);
        return ConversationSegmenter
            .Segment(sanitized, 12)
            .Select(line =>
            {
                var role =
                    line.StartsWith("我：", StringComparison.Ordinal) ||
                    line.StartsWith("我:", StringComparison.Ordinal) ||
                    line.StartsWith("You:", StringComparison.OrdinalIgnoreCase)
                        ? "self"
                        : "other";
                return new ConversationTurn(role, line[..Math.Min(line.Length, 2000)]);
            })
            .ToArray();
    }

    private async Task SendThemeWhenConnectedAsync(
        string theme,
        CancellationToken cancellationToken)
    {
        for (var attempt = 0; attempt < 5; attempt++)
        {
            if (await _pipe.SendThemeAsync(theme, cancellationToken))
            {
                return;
            }
            await Task.Delay(TimeSpan.FromMilliseconds(250), cancellationToken);
        }
    }

    private static (double Left, double Top) OverlayPosition(Rect anchor)
    {
        var workArea = SystemParameters.WorkArea;
        var desiredLeft = anchor.IsEmpty ? workArea.Right - 410 : anchor.Right + 12;
        var desiredTop = anchor.IsEmpty ? workArea.Bottom - 320 : anchor.Bottom + 12;
        return (
            Math.Clamp(desiredLeft, workArea.Left + 8, workArea.Right - 398),
            Math.Clamp(desiredTop, workArea.Top + 8, workArea.Bottom - 310));
    }

    public void Dispose()
    {
        _shutdown.Cancel();
        _debounce?.Cancel();
        _debounce?.Dispose();
        _pipe.Dispose();
        _ocr.Dispose();
        _shutdown.Dispose();
    }
}
