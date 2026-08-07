using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using Forms = System.Windows.Forms;

namespace LangouAssistant.Services;

public sealed class DesktopCaptureService
{
    public Bitmap? CaptureWindow(IntPtr window)
    {
        if (window == IntPtr.Zero || !GetWindowRect(window, out var native))
        {
            return null;
        }

        var requested = Rectangle.FromLTRB(native.Left, native.Top, native.Right, native.Bottom);
        var area = Rectangle.Intersect(requested, Forms.SystemInformation.VirtualScreen);
        if (area.Width < 2 || area.Height < 2)
        {
            return null;
        }

        var bitmap = new Bitmap(area.Width, area.Height, PixelFormat.Format32bppArgb);
        try
        {
            using var graphics = Graphics.FromImage(bitmap);
            graphics.CopyFromScreen(
                area.Location,
                Point.Empty,
                area.Size,
                CopyPixelOperation.SourceCopy);
            return bitmap;
        }
        catch
        {
            bitmap.Dispose();
            throw;
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
    private static extern bool GetWindowRect(IntPtr window, out NativeRect rectangle);
}
