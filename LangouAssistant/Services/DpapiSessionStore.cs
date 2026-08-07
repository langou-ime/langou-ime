using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using LangouAssistant.Core.Storage;

namespace LangouAssistant.Services;

public sealed record StoredSession(
    string AccessToken,
    string RefreshToken,
    string SubjectType,
    DateTimeOffset AccessExpiresAt);

public sealed class DpapiSessionStore
{
    private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("Langou.Ime.Session.v1");
    private readonly string _directory =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Langou");
    private string SessionPath => Path.Combine(_directory, "session.v1.bin");
    private string DevicePath => Path.Combine(_directory, "device.v1");
    private string SettingsPath => Path.Combine(_directory, "settings.v1.json");

    public ClientSettingsLocalStore CreateSettingsStore() =>
        new(SettingsPath);

    public string GetOrCreateDeviceId()
    {
        Directory.CreateDirectory(_directory);
        try
        {
            var existing = File.ReadAllText(DevicePath).Trim();
            if (existing.Length is >= 8 and <= 64)
            {
                return existing;
            }
        }
        catch (IOException)
        {
            // Generate a replacement ID.
        }

        var deviceId = $"dev_{Guid.NewGuid():N}";
        File.WriteAllText(DevicePath, deviceId, new UTF8Encoding(false));
        return deviceId;
    }

    public StoredSession? Load()
    {
        try
        {
            var protectedBytes = File.ReadAllBytes(SessionPath);
            var jsonBytes = ProtectedData.Unprotect(
                protectedBytes,
                Entropy,
                DataProtectionScope.CurrentUser);
            try
            {
                return JsonSerializer.Deserialize<StoredSession>(jsonBytes);
            }
            finally
            {
                CryptographicOperations.ZeroMemory(jsonBytes);
            }
        }
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException or
            CryptographicException or JsonException)
        {
            return null;
        }
    }

    public void Save(StoredSession session)
    {
        Directory.CreateDirectory(_directory);
        var jsonBytes = JsonSerializer.SerializeToUtf8Bytes(session);
        try
        {
            var protectedBytes = ProtectedData.Protect(
                jsonBytes,
                Entropy,
                DataProtectionScope.CurrentUser);
            var temporaryPath = SessionPath + ".new";
            File.WriteAllBytes(temporaryPath, protectedBytes);
            File.Move(temporaryPath, SessionPath, overwrite: true);
        }
        finally
        {
            CryptographicOperations.ZeroMemory(jsonBytes);
        }
    }

    public void Delete()
    {
        try
        {
            File.Delete(SessionPath);
        }
        catch (IOException)
        {
            // Signing out still clears the in-memory session.
        }
    }

    public void PurgeAll()
    {
        LangouLocalState.Purge(_directory);
    }
}
