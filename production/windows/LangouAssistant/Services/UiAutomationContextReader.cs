using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows;
using System.Windows.Automation;
using LangouAssistant.Core.Context;
using LangouAssistant.Core.Privacy;

namespace LangouAssistant.Services;

public sealed record ContextSnapshot(
    IntPtr ForegroundWindow,
    string Application,
    string ProcessName,
    string WindowTitle,
    string AccessibleConversation,
    string? Draft,
    double Confidence,
    Rect Anchor,
    PrivacyDecision Privacy);

public sealed class UiAutomationContextReader
{
    private const uint WdaNone = 0;
    private const int UoiName = 2;

    public Task<ContextSnapshot?> ReadAsync(CancellationToken cancellationToken = default) =>
        Task.Run(Read, cancellationToken);

    private static ContextSnapshot? Read()
    {
        var foreground = GetForegroundWindow();
        if (foreground == IntPtr.Zero)
        {
            return null;
        }

        _ = GetWindowThreadProcessId(foreground, out var processId);
        var processName = GetProcessName(processId);
        var windowTitle = GetWindowTitle(foreground);
        var application = ChatApplicationMapper.FromProcessName(processName);

        AutomationElement? focused;
        try
        {
            focused = AutomationElement.FocusedElement;
        }
        catch (ElementNotAvailableException)
        {
            focused = null;
        }

        var isPassword = ReadIsPassword(focused);
        var screenLabels = ReadSecurityLabels(focused);
        var isSecureDesktop = !string.Equals(GetInputDesktopName(), "Default", StringComparison.OrdinalIgnoreCase);
        var isProtectedWindow =
            GetWindowDisplayAffinity(foreground, out var affinity) && affinity != WdaNone;
        var privacy = SensitiveContextPolicy.Evaluate(
            new ContextDescriptor(
                processName,
                windowTitle,
                isPassword,
                isSecureDesktop,
                isProtectedWindow,
                screenLabels));

        var anchor = GetAnchor(focused, foreground);
        if (!privacy.AllowCapture)
        {
            return new ContextSnapshot(
                foreground,
                application,
                processName,
                windowTitle,
                string.Empty,
                null,
                0,
                anchor,
                privacy);
        }

        var draft = ReadValue(focused);
        var conversation = ReadLongestText(focused);
        var confidence = conversation.Length switch
        {
            >= 20 => 0.92,
            >= 4 => 0.68,
            _ => 0.10,
        };
        return new ContextSnapshot(
            foreground,
            application,
            processName,
            windowTitle,
            conversation,
            draft,
            confidence,
            anchor,
            privacy);
    }

    private static bool ReadIsPassword(AutomationElement? element)
    {
        var current = element;
        for (var depth = 0; depth < 6 && current is not null; depth++)
        {
            try
            {
                if (current.Current.IsPassword)
                {
                    return true;
                }
                current = TreeWalker.ControlViewWalker.GetParent(current);
            }
            catch (ElementNotAvailableException)
            {
                return true;
            }
        }
        return false;
    }

    private static string ReadSecurityLabels(AutomationElement? element)
    {
        const int maximumLength = 512;
        var labels = new List<string>();
        var current = element;
        for (var depth = 0; depth < 6 && current is not null; depth++)
        {
            try
            {
                var name = current.Current.Name.Trim();
                if (!string.IsNullOrWhiteSpace(name) &&
                    !labels.Contains(name, StringComparer.Ordinal))
                {
                    labels.Add(name);
                }
                current = TreeWalker.ControlViewWalker.GetParent(current);
            }
            catch (ElementNotAvailableException)
            {
                break;
            }
        }

        var combined = string.Join('\n', labels);
        return combined.Length <= maximumLength
            ? combined
            : combined[..maximumLength];
    }

    private static string? ReadValue(AutomationElement? element)
    {
        if (element is null)
        {
            return null;
        }

        try
        {
            return element.TryGetCurrentPattern(ValuePattern.Pattern, out var valuePattern)
                ? ((ValuePattern)valuePattern).Current.Value
                : null;
        }
        catch (ElementNotAvailableException)
        {
            return null;
        }
    }

    private static string ReadLongestText(AutomationElement? element)
    {
        var longest = string.Empty;
        var current = element;
        for (var depth = 0; depth < 7 && current is not null; depth++)
        {
            try
            {
                if (current.TryGetCurrentPattern(TextPattern.Pattern, out var pattern))
                {
                    var text = ((TextPattern)pattern).DocumentRange.GetText(6000).Trim();
                    if (text.Length > longest.Length)
                    {
                        longest = text;
                    }
                }
                current = TreeWalker.ControlViewWalker.GetParent(current);
            }
            catch (ElementNotAvailableException)
            {
                break;
            }
        }
        return longest;
    }

    private static Rect GetAnchor(AutomationElement? element, IntPtr foreground)
    {
        try
        {
            var rectangle = element?.Current.BoundingRectangle ?? Rect.Empty;
            if (!rectangle.IsEmpty && rectangle.Width > 0 && rectangle.Height > 0)
            {
                return rectangle;
            }
        }
        catch (ElementNotAvailableException)
        {
            // Fall back to the foreground window.
        }

        return GetWindowRect(foreground, out var nativeRectangle)
            ? new Rect(
                nativeRectangle.Left,
                nativeRectangle.Top,
                nativeRectangle.Right - nativeRectangle.Left,
                nativeRectangle.Bottom - nativeRectangle.Top)
            : Rect.Empty;
    }

    private static string GetProcessName(uint processId)
    {
        try
        {
            using var process = Process.GetProcessById(checked((int)processId));
            return process.ProcessName + ".exe";
        }
        catch (Exception exception) when (
            exception is ArgumentException or InvalidOperationException or OverflowException)
        {
            return string.Empty;
        }
    }

    private static string GetWindowTitle(IntPtr window)
    {
        var length = GetWindowTextLength(window);
        if (length <= 0)
        {
            return string.Empty;
        }
        var text = new StringBuilder(length + 1);
        _ = GetWindowText(window, text, text.Capacity);
        return text.ToString();
    }

    private static string GetInputDesktopName()
    {
        var desktop = OpenInputDesktop(0, false, 0x0001);
        if (desktop == IntPtr.Zero)
        {
            return "Secure";
        }
        try
        {
            _ = GetUserObjectInformation(desktop, UoiName, IntPtr.Zero, 0, out var needed);
            if (needed <= 0)
            {
                return "Secure";
            }
            var buffer = Marshal.AllocHGlobal(needed);
            try
            {
                return GetUserObjectInformation(desktop, UoiName, buffer, needed, out _)
                    ? Marshal.PtrToStringUni(buffer) ?? "Secure"
                    : "Secure";
            }
            finally
            {
                Marshal.FreeHGlobal(buffer);
            }
        }
        finally
        {
            _ = CloseDesktop(desktop);
        }
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct NativeRect
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr window, StringBuilder text, int maximumCount);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowTextLength(IntPtr window);

    [DllImport("user32.dll")]
    private static extern bool GetWindowRect(IntPtr window, out NativeRect rectangle);

    [DllImport("user32.dll")]
    private static extern bool GetWindowDisplayAffinity(IntPtr window, out uint affinity);

    [DllImport("user32.dll")]
    private static extern IntPtr OpenInputDesktop(uint flags, bool inherit, uint desiredAccess);

    [DllImport("user32.dll")]
    private static extern bool CloseDesktop(IntPtr desktop);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern bool GetUserObjectInformation(
        IntPtr handle,
        int index,
        IntPtr information,
        int length,
        out int needed);
}
