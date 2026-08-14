using System.Drawing;
using System.Drawing.Imaging;
using System.Security.Cryptography;
using OpenCvSharp;
using Sdcb.OpenVINO;
using Sdcb.OpenVINO.PaddleOCR;
using Sdcb.OpenVINO.PaddleOCR.Models;

namespace LangouAssistant.Services;

public sealed class PaddleOcrService : IDisposable
{
    private readonly SemaphoreSlim _inferenceLock = new(1, 1);
    private PaddleOcrAll? _ocr;

    public async Task<string> RecognizeAsync(
        Bitmap screenshot,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(screenshot);
        await _inferenceLock.WaitAsync(cancellationToken);
        try
        {
            return await Task.Run(() => Recognize(screenshot), cancellationToken);
        }
        finally
        {
            _inferenceLock.Release();
        }
    }

    private string Recognize(Bitmap screenshot)
    {
        _ocr ??= CreateEngine();
        using var encoded = new MemoryStream();
        screenshot.Save(encoded, ImageFormat.Png);
        var bytes = encoded.ToArray();
        try
        {
            using var image = Cv2.ImDecode(bytes, ImreadModes.Color);
            var result = _ocr.Run(image);
            return string.Join(
                '\n',
                result.Regions
                    .Where(region => region.Score >= 0.60f && !string.IsNullOrWhiteSpace(region.Text))
                    .OrderBy(region => region.Rect.Center.Y)
                    .ThenBy(region => region.Rect.Center.X)
                    .Select(region => region.Text.Trim()));
        }
        finally
        {
            CryptographicOperations.ZeroMemory(bytes);
        }
    }

    private static PaddleOcrAll CreateEngine()
    {
        var root = Path.Combine(AppContext.BaseDirectory, "Models", "PP-OCRv6-tiny");
        var detectionPath = Path.Combine(root, "det");
        var recognitionPath = Path.Combine(root, "rec");
        if (!File.Exists(Path.Combine(detectionPath, "inference.onnx")) ||
            !File.Exists(Path.Combine(recognitionPath, "inference.onnx")) ||
            !File.Exists(Path.Combine(recognitionPath, "inference.yml")))
        {
            throw new FileNotFoundException("PP-OCRv6 tiny 本地模型不完整。");
        }

        var model = new FullOcrModel(
            DetectionModel.FromDirectory(detectionPath, ModelVersion.V6),
            RecognizationModel.FromV6Directory(recognitionPath));
        return new PaddleOcrAll(model, new PaddleOcrOptions(new DeviceOptions("CPU")))
        {
            AllowRotateDetection = true,
            Enable180Classification = false,
            EnableDocumentOrientationClassification = false,
        };
    }

    public void Dispose()
    {
        _ocr?.Dispose();
        _inferenceLock.Dispose();
    }
}
