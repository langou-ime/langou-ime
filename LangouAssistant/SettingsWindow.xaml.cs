using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using LangouAssistant.Core.Api;
using LangouAssistant.Core.Update;
using LangouAssistant.Services;

namespace LangouAssistant;

public partial class SettingsWindow : Window
{
    private readonly AssistantCoordinator _coordinator;

    public SettingsWindow(AssistantCoordinator coordinator)
    {
        InitializeComponent();
        _coordinator = coordinator;
        LoadCurrentSettings();
    }

    private void LoadCurrentSettings()
    {
        var settings = _coordinator.Settings;
        ThemeBox.SelectedValue = settings.Theme;
        if (ThemeBox.SelectedIndex < 0) ThemeBox.SelectedIndex = 0;
        AutoSuggestBox.IsChecked = settings.AutoSuggest;
        SaveHistoryBox.IsChecked = settings.SaveHistory;
        DiagnosticsBox.IsChecked = settings.Diagnostics;
        AccountStatus.Text = _coordinator.SubjectType == "user"
            ? "已登录，设置和历史会跨设备同步"
            : "游客也能正常输入和使用 AI";
    }

    private async void SendSms_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            var response = await _coordinator.SendSmsAsync(PhoneBox.Text);
            ActionStatus.Text = $"验证码已发送，{response.RetryAfter} 秒后可重试。";
        });
    }

    private async void VerifySms_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            await _coordinator.VerifySmsAsync(PhoneBox.Text, CodeBox.Text);
            AccountStatus.Text = "已登录，游客历史已合并";
            ActionStatus.Text = "登录成功。";
            LoadCurrentSettings();
        });
    }

    private async void SaveSettings_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            var theme = (ThemeBox.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? "cream";
            var result = await _coordinator.SaveSettingsAsync(
                new ClientSettings(
                    theme,
                    AutoSuggestBox.IsChecked == true,
                    SaveHistoryBox.IsChecked == true,
                    DiagnosticsBox.IsChecked == true));
            ActionStatus.Text = result.CloudSynced
                ? "设置已保存并同步。"
                : "设置已保存到本机，联网后可再次同步。";
        });
    }

    private async void ClearHistory_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            await _coordinator.ClearHistoryAsync();
            ActionStatus.Text = "云端 AI 历史已全部清空。";
        });
    }

    private async void CheckUpdate_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            var result = await _coordinator.CheckForUpdateAsync();
            ActionStatus.Text = result.Message;
            if (result.DownloadUri is not null &&
                result.Decision is ReleaseDecision.Optional or ReleaseDecision.Mandatory)
            {
                _ = Process.Start(new ProcessStartInfo(result.DownloadUri.AbsoluteUri)
                {
                    UseShellExecute = true,
                });
            }
        });
    }

    private void PrivacyPolicy_Click(object sender, RoutedEventArgs e)
    {
        _ = Process.Start(
            new ProcessStartInfo(
                "https://github.com/langou-ime/windows/blob/main/PRIVACY.md")
            {
                UseShellExecute = true,
            });
    }

    private async Task RunActionAsync(Func<Task> action)
    {
        IsEnabled = false;
        ActionStatus.Text = "请稍候…";
        try
        {
            await action();
        }
        catch (Exception exception) when (
            exception is HttpRequestException or TaskCanceledException or ArgumentException)
        {
            ActionStatus.Text = exception is ArgumentException
                ? exception.Message
                : "网络暂时不可用，普通输入不受影响。";
        }
        finally
        {
            IsEnabled = true;
        }
    }
}
