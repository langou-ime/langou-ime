using System.Net;
using LangouAssistant.Core.Api;
using LangouAssistant.Core.Storage;

namespace LangouAssistant.Tests;

public sealed class LocalStatePolicyTests
{
    [Theory]
    [InlineData("/background", AssistantLaunchCommand.Background)]
    [InlineData("/settings", AssistantLaunchCommand.Settings)]
    [InlineData("/update", AssistantLaunchCommand.Settings)]
    [InlineData("/quit", AssistantLaunchCommand.Quit)]
    [InlineData("/quit-and-purge", AssistantLaunchCommand.QuitAndPurge)]
    [InlineData("/QUIT-AND-PURGE", AssistantLaunchCommand.QuitAndPurge)]
    public void Launch_commands_are_parsed_explicitly(
        string argument,
        AssistantLaunchCommand expected)
    {
        Assert.Equal(expected, AssistantLaunchCommands.Parse([argument]));
    }

    [Fact]
    public void Unknown_launch_arguments_do_not_trigger_a_privileged_action()
    {
        Assert.Equal(
            AssistantLaunchCommand.Background,
            AssistantLaunchCommands.Parse(["/delete-everything"]));
    }

    [Fact]
    public void Expired_refresh_token_may_fall_back_to_a_guest()
    {
        var exception = new HttpRequestException(
            "expired",
            inner: null,
            HttpStatusCode.Unauthorized);

        Assert.True(SessionRefreshPolicy.ShouldCreateReplacementGuest(exception));
    }

    [Theory]
    [InlineData(HttpStatusCode.BadGateway)]
    [InlineData(HttpStatusCode.ServiceUnavailable)]
    [InlineData(HttpStatusCode.TooManyRequests)]
    public void Transient_server_failures_must_not_replace_an_account(HttpStatusCode status)
    {
        var exception = new HttpRequestException("temporary", inner: null, status);

        Assert.False(SessionRefreshPolicy.ShouldCreateReplacementGuest(exception));
    }

    [Fact]
    public void Network_failure_without_a_status_must_not_replace_an_account()
    {
        Assert.False(SessionRefreshPolicy.ShouldCreateReplacementGuest(
            new HttpRequestException("offline")));
    }

    [Fact]
    public void Purge_deletes_only_the_named_langou_state_directory()
    {
        var root = Path.Combine(
            Path.GetTempPath(),
            $"langou-cleanup-test-{Guid.NewGuid():N}");
        var state = Path.Combine(root, "Langou");
        var sibling = Path.Combine(root, "KeepMe");
        Directory.CreateDirectory(state);
        Directory.CreateDirectory(sibling);
        File.WriteAllText(Path.Combine(state, "session.v1.bin"), "secret");
        File.WriteAllText(Path.Combine(state, "device.v1"), "device");
        File.WriteAllText(Path.Combine(sibling, "notes.txt"), "keep");

        try
        {
            LangouLocalState.Purge(state);

            Assert.False(Directory.Exists(state));
            Assert.True(File.Exists(Path.Combine(sibling, "notes.txt")));
        }
        finally
        {
            if (Directory.Exists(root))
            {
                Directory.Delete(root, recursive: true);
            }
        }
    }

    [Fact]
    public void Purge_rejects_a_directory_not_named_langou()
    {
        var unsafeTarget = Path.Combine(Path.GetTempPath(), "NotLangou");

        Assert.Throws<ArgumentException>(() => LangouLocalState.Purge(unsafeTarget));
    }

    [Fact]
    public void Local_settings_round_trip_for_offline_use()
    {
        var directory = Path.Combine(
            Path.GetTempPath(),
            $"langou-settings-test-{Guid.NewGuid():N}",
            "Langou");
        var path = Path.Combine(directory, "settings.v1.json");
        var store = new ClientSettingsLocalStore(path);
        var expected = new ClientSettings("moon", false, false, true);

        try
        {
            store.Save(expected);

            Assert.Equal(expected, store.Load());
        }
        finally
        {
            if (Directory.Exists(directory))
            {
                Directory.Delete(directory, recursive: true);
            }
        }
    }

    [Theory]
    [InlineData("downloaded")]
    [InlineData("")]
    public void Local_settings_reject_unknown_themes(string theme)
    {
        var path = Path.Combine(
            Path.GetTempPath(),
            $"langou-settings-test-{Guid.NewGuid():N}",
            "Langou",
            "settings.v1.json");
        var store = new ClientSettingsLocalStore(path);

        Assert.Throws<ArgumentException>(
            () => store.Save(new ClientSettings(theme, true, true, false)));
    }
}
