using System.Text.Json;
using LangouAssistant.Core.Api;

namespace LangouAssistant.Core.Storage;

public sealed class ClientSettingsLocalStore
{
    private static readonly HashSet<string> Themes =
        new(StringComparer.Ordinal) { "cream", "soda", "moon" };
    private static readonly JsonSerializerOptions JsonOptions =
        new(JsonSerializerDefaults.Web)
        {
            PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
            UnmappedMemberHandling = System.Text.Json.Serialization.JsonUnmappedMemberHandling.Disallow,
        };
    private readonly string _path;

    public ClientSettingsLocalStore(string path)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(path);
        _path = Path.GetFullPath(path);
    }

    public ClientSettings? Load()
    {
        try
        {
            var settings = JsonSerializer.Deserialize<ClientSettings>(
                File.ReadAllText(_path),
                JsonOptions);
            return settings is not null && Themes.Contains(settings.Theme)
                ? settings
                : null;
        }
        catch (Exception exception) when (
            exception is IOException or UnauthorizedAccessException or JsonException)
        {
            return null;
        }
    }

    public void Save(ClientSettings settings)
    {
        ArgumentNullException.ThrowIfNull(settings);
        if (!Themes.Contains(settings.Theme))
        {
            throw new ArgumentException("Unsupported Langou theme.", nameof(settings));
        }

        var directory = Path.GetDirectoryName(_path)
            ?? throw new ArgumentException("Settings path has no parent directory.");
        Directory.CreateDirectory(directory);
        var temporaryPath = _path + ".new";
        File.WriteAllText(
            temporaryPath,
            JsonSerializer.Serialize(settings, JsonOptions),
            new System.Text.UTF8Encoding(encoderShouldEmitUTF8Identifier: false));
        File.Move(temporaryPath, _path, overwrite: true);
    }
}
