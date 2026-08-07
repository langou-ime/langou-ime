using LangouAssistant.Core.Api;
using LangouAssistant.Core.Storage;

namespace LangouAssistant.Services;

public sealed class SessionManager
{
    private const string AppVersion = "1.0.0";
    private readonly LangouApiClient _api;
    private readonly DpapiSessionStore _store;
    private readonly SemaphoreSlim _sessionLock = new(1, 1);
    private StoredSession? _session;

    public SessionManager(LangouApiClient api, DpapiSessionStore store)
    {
        _api = api;
        _store = store;
        _session = store.Load();
        DeviceId = store.GetOrCreateDeviceId();
    }

    public string DeviceId { get; }
    public string SubjectType => _session?.SubjectType ?? "guest";

    public async Task<string> GetAccessTokenAsync(CancellationToken cancellationToken = default)
    {
        await _sessionLock.WaitAsync(cancellationToken);
        try
        {
            if (_session is not null &&
                _session.AccessExpiresAt > DateTimeOffset.UtcNow.AddSeconds(30))
            {
                return _session.AccessToken;
            }

            TokenPair pair;
            if (_session is not null)
            {
                try
                {
                    pair = await _api.RefreshTokenAsync(_session.RefreshToken, cancellationToken);
                }
                catch (HttpRequestException exception)
                    when (SessionRefreshPolicy.ShouldCreateReplacementGuest(exception))
                {
                    pair = await _api.CreateGuestSessionAsync(DeviceId, AppVersion, cancellationToken);
                }
            }
            else
            {
                pair = await _api.CreateGuestSessionAsync(DeviceId, AppVersion, cancellationToken);
            }

            Replace(pair);
            return pair.AccessToken;
        }
        finally
        {
            _sessionLock.Release();
        }
    }

    public Task<SmsSendResponse> SendSmsAsync(
        string phone,
        CancellationToken cancellationToken = default) =>
        _api.SendSmsAsync(NormalizePhone(phone), cancellationToken);

    public async Task VerifySmsAsync(
        string phone,
        string code,
        CancellationToken cancellationToken = default)
    {
        var guestRefresh = _session is { SubjectType: "guest" } ? _session.RefreshToken : null;
        var userPair = await _api.VerifySmsAsync(
            NormalizePhone(phone),
            code.Trim(),
            DeviceId,
            cancellationToken);

        if (guestRefresh is not null)
        {
            try
            {
                await _api.MergeGuestAsync(guestRefresh, userPair.AccessToken, cancellationToken);
            }
            catch (HttpRequestException)
            {
                // Login succeeds even if an already-consumed guest has nothing left to merge.
            }
        }

        Replace(userPair);
    }

    public void SignOut()
    {
        _session = null;
        _store.Delete();
    }

    private void Replace(TokenPair pair)
    {
        _session = new StoredSession(
            pair.AccessToken,
            pair.RefreshToken,
            pair.SubjectType,
            DateTimeOffset.UtcNow.AddSeconds(Math.Max(60, pair.ExpiresIn)));
        _store.Save(_session);
    }

    private static string NormalizePhone(string input)
    {
        var digits = new string(input.Where(char.IsDigit).ToArray());
        if (digits.StartsWith("86", StringComparison.Ordinal) && digits.Length == 13)
        {
            digits = digits[2..];
        }
        if (digits.Length != 11 || digits[0] != '1')
        {
            throw new ArgumentException("请输入有效的中国大陆 11 位手机号。", nameof(input));
        }
        return $"+86{digits}";
    }
}
