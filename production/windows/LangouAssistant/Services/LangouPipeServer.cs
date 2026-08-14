using System.Collections.Concurrent;
using System.IO.Pipes;
using System.IO;
using System.Text;
using LangouAssistant.Core.Protocol;

namespace LangouAssistant.Services;

public sealed class LangouPipeServer : IDisposable
{
    private readonly CancellationTokenSource _shutdown = new();
    private readonly SemaphoreSlim _writerLock = new(1, 1);
    private readonly ConcurrentDictionary<string, TaskCompletionSource<bool>> _pendingCommits = new();
    private StreamWriter? _writer;
    private NamedPipeServerStream? _pipe;
    private Task? _acceptLoop;

    public void Start()
    {
        _acceptLoop ??= Task.Run(() => AcceptLoopAsync(_shutdown.Token));
    }

    public async Task<bool> SendCommitAsync(
        PipeCommand command,
        CancellationToken cancellationToken = default)
    {
        var json = LangouPipeProtocol.Serialize(command);
        var acknowledgement =
            new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        if (!_pendingCommits.TryAdd(command.RequestId, acknowledgement))
        {
            return false;
        }

        try
        {
            if (!await SendLineAsync(json, cancellationToken))
            {
                return false;
            }
            return await acknowledgement.Task.WaitAsync(
                TimeSpan.FromSeconds(2),
                cancellationToken);
        }
        catch (TimeoutException)
        {
            return false;
        }
        finally
        {
            _pendingCommits.TryRemove(command.RequestId, out _);
        }
    }

    public Task<bool> SendThemeAsync(
        string theme,
        CancellationToken cancellationToken = default) =>
        SendLineAsync(LangouPipeProtocol.SerializeTheme(theme), cancellationToken);

    private async Task AcceptLoopAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            using var pipe = new NamedPipeServerStream(
                LangouPipeProtocol.PipeName,
                PipeDirection.InOut,
                1,
                PipeTransmissionMode.Byte,
                PipeOptions.Asynchronous | PipeOptions.CurrentUserOnly,
                4096,
                4096);
            _pipe = pipe;
            try
            {
                await pipe.WaitForConnectionAsync(cancellationToken);
                using var reader = new StreamReader(
                    pipe,
                    Encoding.UTF8,
                    detectEncodingFromByteOrderMarks: false,
                    bufferSize: 4096,
                    leaveOpen: true);
                using var writer = new StreamWriter(
                    pipe,
                    new UTF8Encoding(encoderShouldEmitUTF8Identifier: false),
                    bufferSize: 4096,
                    leaveOpen: true)
                {
                    AutoFlush = true,
                };
                await _writerLock.WaitAsync(cancellationToken);
                try
                {
                    _writer = writer;
                }
                finally
                {
                    _writerLock.Release();
                }

                while (!cancellationToken.IsCancellationRequested &&
                       await reader.ReadLineAsync(cancellationToken) is { } line)
                {
                    HandleIncomingLine(line);
                }
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                break;
            }
            catch (IOException)
            {
                // Weasel restarts independently; accept a fresh same-user connection.
            }
            finally
            {
                await _writerLock.WaitAsync(CancellationToken.None);
                try
                {
                    _writer = null;
                    _pipe = null;
                }
                finally
                {
                    _writerLock.Release();
                }
                RejectPendingCommits();
            }
        }
    }

    private void HandleIncomingLine(string line)
    {
        try
        {
            var acknowledgement = LangouPipeProtocol.ParseAcknowledgement(line);
            if (_pendingCommits.TryRemove(acknowledgement.RequestId, out var pending))
            {
                pending.TrySetResult(acknowledgement.Accepted);
            }
        }
        catch (ProtocolException)
        {
            // Hello and malformed messages carry no user content and are ignored.
        }
    }

    private void RejectPendingCommits()
    {
        foreach (var requestId in _pendingCommits.Keys)
        {
            if (_pendingCommits.TryRemove(requestId, out var pending))
            {
                pending.TrySetResult(false);
            }
        }
    }

    private async Task<bool> SendLineAsync(
        string json,
        CancellationToken cancellationToken)
    {
        await _writerLock.WaitAsync(cancellationToken);
        try
        {
            if (_writer is null || _pipe is not { IsConnected: true })
            {
                return false;
            }

            await _writer.WriteLineAsync(json.AsMemory(), cancellationToken);
            await _writer.FlushAsync(cancellationToken);
            return true;
        }
        catch (IOException)
        {
            return false;
        }
        finally
        {
            _writerLock.Release();
        }
    }

    public void Dispose()
    {
        _shutdown.Cancel();
        _pipe?.Dispose();
        RejectPendingCommits();
        try
        {
            _acceptLoop?.Wait(TimeSpan.FromSeconds(1));
        }
        catch (AggregateException)
        {
            // Shutdown is best effort.
        }
        _writerLock.Dispose();
        _shutdown.Dispose();
    }
}
