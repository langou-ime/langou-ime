using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.Encodings.Web;

namespace LangouAssistant.Core.Protocol;

public sealed record PipeCommand(
    [property: JsonPropertyName("version")] int Version,
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("request_id")] string RequestId,
    [property: JsonPropertyName("text")] string Text);

public sealed record PipeAcknowledgement(
    [property: JsonPropertyName("version")] int Version,
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("request_id")] string RequestId,
    [property: JsonPropertyName("accepted")] bool Accepted);

public sealed class ProtocolException(string message, Exception? innerException = null)
    : Exception(message, innerException);

public static class LangouPipeProtocol
{
    public const int CurrentVersion = 1;
    public const int MaxCommitTextLength = 1000;
    public const string PipeName = "Langou.Ime.v1";

    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };

    public static PipeCommand ParseCommitText(string json)
    {
        PipeCommand? command;
        try
        {
            command = JsonSerializer.Deserialize<PipeCommand>(json, SerializerOptions);
        }
        catch (JsonException exception)
        {
            throw new ProtocolException("管道消息不是有效的 JSON 协议消息。", exception);
        }

        if (command is null)
        {
            throw new ProtocolException("管道消息不能为空。");
        }

        if (command.Version != CurrentVersion)
        {
            throw new ProtocolException("不支持的管道协议版本。");
        }

        if (!string.Equals(command.Type, "commit_text", StringComparison.Ordinal))
        {
            throw new ProtocolException("仅允许 commit_text，不提供自动发送指令。");
        }

        if (string.IsNullOrWhiteSpace(command.RequestId) || command.RequestId.Length > 128)
        {
            throw new ProtocolException("request_id 无效。");
        }

        if (string.IsNullOrWhiteSpace(command.Text) || command.Text.Length > MaxCommitTextLength)
        {
            throw new ProtocolException("提交文字为空或超过 1000 个字符。");
        }

        return command;
    }

    public static string Serialize(PipeCommand command)
    {
        _ = ParseCommitText(JsonSerializer.Serialize(command, SerializerOptions));
        return JsonSerializer.Serialize(command, SerializerOptions);
    }

    public static PipeAcknowledgement ParseAcknowledgement(string json)
    {
        PipeAcknowledgement? acknowledgement;
        try
        {
            acknowledgement =
                JsonSerializer.Deserialize<PipeAcknowledgement>(json, SerializerOptions);
        }
        catch (JsonException exception)
        {
            throw new ProtocolException("管道确认不是有效的 JSON 协议消息。", exception);
        }

        if (acknowledgement is null)
        {
            throw new ProtocolException("管道确认不能为空。");
        }
        if (acknowledgement.Version != CurrentVersion ||
            !string.Equals(acknowledgement.Type, "ack", StringComparison.Ordinal))
        {
            throw new ProtocolException("不支持的管道确认类型或版本。");
        }
        if (string.IsNullOrWhiteSpace(acknowledgement.RequestId) ||
            acknowledgement.RequestId.Length > 128)
        {
            throw new ProtocolException("确认 request_id 无效。");
        }

        return acknowledgement;
    }

    public static string SerializeHello(string appVersion)
    {
        if (string.IsNullOrWhiteSpace(appVersion) || appVersion.Length > 32)
        {
            throw new ProtocolException("助手版本号无效。");
        }

        return JsonSerializer.Serialize(
            new
            {
                version = CurrentVersion,
                type = "hello",
                app_version = appVersion,
                capabilities = new[] { "commit_text" },
            },
            SerializerOptions);
    }

    public static string SerializeTheme(string theme)
    {
        if (theme is not ("cream" or "soda" or "moon"))
        {
            throw new ProtocolException("不支持的内置皮肤。");
        }

        return JsonSerializer.Serialize(
            new
            {
                version = CurrentVersion,
                type = "set_theme",
                theme,
            },
            SerializerOptions);
    }
}
