using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using LangouAssistant.Core.Api;
using LangouAssistant.Services;

namespace LangouAssistant;

public partial class MainWindow : Window
{
    private const int GwlExStyle = -20;
    private const int WsExNoActivate = 0x08000000;
    private const int WsExToolWindow = 0x00000080;
    private AssistantCoordinator? _coordinator;
    private IReadOnlyList<Suggestion> _suggestions = [];

    public MainWindow()
    {
        InitializeComponent();
        SourceInitialized += (_, _) =>
        {
            var handle = new WindowInteropHelper(this).Handle;
            var style = GetWindowLongPtr(handle, GwlExStyle).ToInt64();
            _ = SetWindowLongPtr(handle, GwlExStyle, new IntPtr(style | WsExNoActivate | WsExToolWindow));
        };
    }

    public void Attach(AssistantCoordinator coordinator)
    {
        _coordinator = coordinator;
    }

    public void ShowLoading(double left, double top)
    {
        Dispatcher.Invoke(() =>
        {
            Left = left;
            Top = top;
            StatusText.Text = "懒狗正在想三种回法…";
            SuggestionPanel.Visibility = Visibility.Collapsed;
            if (!IsVisible) Show();
        });
    }

    public void ShowSuggestions(IReadOnlyList<Suggestion> suggestions, double left, double top)
    {
        Dispatcher.Invoke(() =>
        {
            _suggestions = suggestions.Take(3).ToArray();
            if (_suggestions.Count == 0)
            {
                Hide();
                return;
            }

            Left = left;
            Top = top;
            var buttons = new[] { SuggestionOne, SuggestionTwo, SuggestionThree };
            for (var index = 0; index < buttons.Length; index++)
            {
                buttons[index].Visibility = index < _suggestions.Count
                    ? Visibility.Visible
                    : Visibility.Collapsed;
                if (index < _suggestions.Count)
                {
                    buttons[index].Content = _suggestions[index].Text;
                }
            }
            StatusText.Text = "点一下，只写入，不会发送";
            SuggestionPanel.Visibility = Visibility.Visible;
            if (!IsVisible) Show();
        });
    }

    public void ShowUnavailable()
    {
        Dispatcher.Invoke(Hide);
    }

    private async void Suggestion_Click(object sender, RoutedEventArgs e)
    {
        if (_coordinator is null ||
            sender is not System.Windows.Controls.Button { Tag: string indexValue } ||
            !int.TryParse(indexValue, out var index))
        {
            return;
        }

        StatusText.Text = "正在写入输入框…";
        if (await _coordinator.CommitSuggestionAsync(index))
        {
            Hide();
        }
        else
        {
            StatusText.Text = "输入框已失去焦点，请回到聊天窗口重试";
        }
    }

    private void CloseButton_Click(object sender, RoutedEventArgs e) => Hide();

    private void SettingsButton_Click(object sender, RoutedEventArgs e)
    {
        if (_coordinator is null)
        {
            return;
        }

        var settings = new SettingsWindow(_coordinator);
        settings.Show();
        settings.Activate();
    }

    [DllImport("user32.dll", EntryPoint = "GetWindowLongPtrW")]
    private static extern IntPtr GetWindowLongPtr(IntPtr window, int index);

    [DllImport("user32.dll", EntryPoint = "SetWindowLongPtrW")]
    private static extern IntPtr SetWindowLongPtr(IntPtr window, int index, IntPtr value);
}
