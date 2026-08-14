using System.Net;

namespace LangouAssistant.Core.Storage;

public enum AssistantLaunchCommand
{
    Background,
    Settings,
    Quit,
    QuitAndPurge,
}

public static class AssistantLaunchCommands
{
    public static AssistantLaunchCommand Parse(IEnumerable<string> arguments)
    {
        ArgumentNullException.ThrowIfNull(arguments);
        foreach (var argument in arguments)
        {
            if (string.Equals(argument, "/quit-and-purge", StringComparison.OrdinalIgnoreCase))
            {
                return AssistantLaunchCommand.QuitAndPurge;
            }
            if (string.Equals(argument, "/quit", StringComparison.OrdinalIgnoreCase))
            {
                return AssistantLaunchCommand.Quit;
            }
            if (string.Equals(argument, "/settings", StringComparison.OrdinalIgnoreCase) ||
                string.Equals(argument, "/update", StringComparison.OrdinalIgnoreCase))
            {
                return AssistantLaunchCommand.Settings;
            }
        }
        return AssistantLaunchCommand.Background;
    }
}

public static class SessionRefreshPolicy
{
    public static bool ShouldCreateReplacementGuest(HttpRequestException exception)
    {
        ArgumentNullException.ThrowIfNull(exception);
        return exception.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden;
    }
}

public static class LangouLocalState
{
    public static void Purge(string stateDirectory)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(stateDirectory);
        var fullPath = Path.GetFullPath(stateDirectory);
        var leafName = new DirectoryInfo(fullPath).Name;
        if (!string.Equals(leafName, "Langou", StringComparison.Ordinal))
        {
            throw new ArgumentException(
                "Only the dedicated Langou local-state directory may be purged.",
                nameof(stateDirectory));
        }

        if (Directory.Exists(fullPath))
        {
            Directory.Delete(fullPath, recursive: true);
        }
    }
}
