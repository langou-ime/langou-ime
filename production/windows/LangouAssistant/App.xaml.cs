using System.Drawing;
using System.Threading;
using LangouAssistant.Core.Storage;
using LangouAssistant.Services;
using Forms = System.Windows.Forms;

namespace LangouAssistant;

public partial class App : System.Windows.Application
{
    private Mutex? _singleInstance;
    private bool _ownsSingleInstance;
    private EventWaitHandle? _showSettingsEvent;
    private EventWaitHandle? _quitEvent;
    private EventWaitHandle? _purgeEvent;
    private CancellationTokenSource? _eventShutdown;
    private Forms.NotifyIcon? _trayIcon;
    private AssistantCoordinator? _coordinator;
    private MainWindow? _mainWindow;

    protected override void OnStartup(System.Windows.StartupEventArgs e)
    {
        base.OnStartup(e);
        var launchCommand = AssistantLaunchCommands.Parse(e.Args);
        _singleInstance = new Mutex(true, @"Local\Langou.Assistant.v1", out var created);
        _ownsSingleInstance = created;
        if (!created)
        {
            var eventName = launchCommand switch
            {
                AssistantLaunchCommand.Settings => @"Local\Langou.Assistant.ShowSettings.v1",
                AssistantLaunchCommand.Quit => @"Local\Langou.Assistant.Quit.v1",
                AssistantLaunchCommand.QuitAndPurge => @"Local\Langou.Assistant.Purge.v1",
                _ => null,
            };
            if (eventName is not null)
            {
                SignalPrimaryInstance(eventName);
                try
                {
                    _ownsSingleInstance = _singleInstance.WaitOne(TimeSpan.FromSeconds(8));
                }
                catch (AbandonedMutexException)
                {
                    _ownsSingleInstance = true;
                }
            }
            Shutdown();
            return;
        }

        if (launchCommand == AssistantLaunchCommand.QuitAndPurge)
        {
            new DpapiSessionStore().PurgeAll();
            Shutdown();
            return;
        }
        if (launchCommand == AssistantLaunchCommand.Quit)
        {
            Shutdown();
            return;
        }

        _showSettingsEvent = new EventWaitHandle(
            false,
            EventResetMode.AutoReset,
            @"Local\Langou.Assistant.ShowSettings.v1");
        _quitEvent = new EventWaitHandle(
            false,
            EventResetMode.AutoReset,
            @"Local\Langou.Assistant.Quit.v1");
        _purgeEvent = new EventWaitHandle(
            false,
            EventResetMode.AutoReset,
            @"Local\Langou.Assistant.Purge.v1");
        _eventShutdown = new CancellationTokenSource();
        _ = Task.Run(() => WaitForControlRequest(_eventShutdown.Token));

        var api = new LangouAssistant.Core.Api.LangouApiClient(
            new HttpClient
            {
                BaseAddress = new Uri("https://api.langou.tech/"),
                Timeout = TimeSpan.FromSeconds(12),
            });
        var sessionStore = new DpapiSessionStore();
        var sessionManager = new SessionManager(api, sessionStore);
        var pipeServer = new LangouPipeServer();
        var contextReader = new UiAutomationContextReader();
        var captureService = new DesktopCaptureService();
        var ocrService = new PaddleOcrService();

        _mainWindow = new MainWindow();
        _coordinator = new AssistantCoordinator(
            _mainWindow,
            api,
            sessionManager,
            pipeServer,
            contextReader,
            captureService,
            ocrService,
            sessionStore.CreateSettingsStore());
        _mainWindow.Attach(_coordinator);
        _mainWindow.Show();
        _mainWindow.Hide();

        _trayIcon = new Forms.NotifyIcon
        {
            Icon = new Icon(GetResourceStream(new Uri("pack://application:,,,/Assets/Langou.ico")).Stream),
            Text = "懒狗输入法助手",
            Visible = true,
            ContextMenuStrip = BuildTrayMenu(),
        };
        _trayIcon.DoubleClick += (_, _) => ShowSettings();

        _ = _coordinator.StartAsync();
        if (launchCommand == AssistantLaunchCommand.Settings)
        {
            ShowSettings();
        }
    }

    private Forms.ContextMenuStrip BuildTrayMenu()
    {
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("账号与设置", null, (_, _) => ShowSettings());
        menu.Items.Add("退出懒狗助手", null, (_, _) => Shutdown());
        return menu;
    }

    private void ShowSettings()
    {
        if (_coordinator is null)
        {
            return;
        }

        var window = new SettingsWindow(_coordinator);
        window.Show();
        window.Activate();
    }

    private static void SignalPrimaryInstance(string eventName)
    {
        for (var attempt = 0; attempt < 20; attempt++)
        {
            try
            {
                using var existingEvent = EventWaitHandle.OpenExisting(eventName);
                existingEvent.Set();
                return;
            }
            catch (WaitHandleCannotBeOpenedException)
            {
                Thread.Sleep(50);
            }
        }
    }

    private void WaitForControlRequest(CancellationToken cancellationToken)
    {
        var handles = new WaitHandle[]
        {
            _showSettingsEvent!,
            _quitEvent!,
            _purgeEvent!,
            cancellationToken.WaitHandle,
        };
        while (!cancellationToken.IsCancellationRequested)
        {
            switch (WaitHandle.WaitAny(handles))
            {
                case 0:
                    _ = Dispatcher.BeginInvoke(ShowSettings);
                    break;
                case 1:
                    _ = Dispatcher.BeginInvoke(() => Shutdown());
                    return;
                case 2:
                    _ = Dispatcher.BeginInvoke(PurgeAndShutdown);
                    return;
                default:
                    return;
            }
        }
    }

    private void PurgeAndShutdown()
    {
        _coordinator?.Dispose();
        _coordinator = null;
        new DpapiSessionStore().PurgeAll();
        Shutdown();
    }

    protected override void OnExit(System.Windows.ExitEventArgs e)
    {
        _coordinator?.Dispose();
        _eventShutdown?.Cancel();
        _showSettingsEvent?.Set();
        _quitEvent?.Set();
        _purgeEvent?.Set();
        if (_trayIcon is not null)
        {
            _trayIcon.Visible = false;
            _trayIcon.Dispose();
        }
        if (_ownsSingleInstance)
        {
            _singleInstance?.ReleaseMutex();
            _ownsSingleInstance = false;
        }
        _singleInstance?.Dispose();
        _showSettingsEvent?.Dispose();
        _quitEvent?.Dispose();
        _purgeEvent?.Dispose();
        _eventShutdown?.Dispose();
        base.OnExit(e);
    }
}
