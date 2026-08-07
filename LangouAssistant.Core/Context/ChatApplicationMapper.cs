namespace LangouAssistant.Core.Context;

public static class ChatApplicationMapper
{
    private static readonly IReadOnlyDictionary<string, string> ProcessMap =
        new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            ["wechat"] = "wechat",
            ["qq"] = "qq",
            ["wxwork"] = "wecom",
            ["wecom"] = "wecom",
            ["dingtalk"] = "dingtalk",
            ["feishu"] = "feishu",
            ["lark"] = "feishu",
            ["whatsapp"] = "whatsapp",
            ["telegram"] = "telegram",
            ["discord"] = "discord",
        };

    public static string FromProcessName(string processName)
    {
        if (string.IsNullOrWhiteSpace(processName))
        {
            return "generic";
        }

        var normalized = Path.GetFileNameWithoutExtension(processName.Trim());
        return ProcessMap.TryGetValue(normalized, out var application)
            ? application
            : "generic";
    }

    public static bool IsAutomaticSuggestionTarget(string application) =>
        !string.Equals(application, "generic", StringComparison.Ordinal) &&
        ProcessMap.Values.Contains(application, StringComparer.Ordinal);
}
