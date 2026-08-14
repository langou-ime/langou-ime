#include "stdafx.h"
#include "LangouBridgeClient.h"

#include "LangouBridgeProtocol.h"

#include <chrono>
#include <utility>

namespace {
constexpr wchar_t kPipePath[] = L"\\\\.\\pipe\\Langou.Ime.v1";
}

LangouBridgeClient::LangouBridgeClient(CommitHandler commit_handler,
                                       ThemeHandler theme_handler)
    : m_commit_handler(std::move(commit_handler)),
      m_theme_handler(std::move(theme_handler)) {}

LangouBridgeClient::~LangouBridgeClient() {
  Stop();
}

void LangouBridgeClient::Start() {
  if (m_running.exchange(true))
    return;
  m_thread = std::thread([this] { Run(); });
}

void LangouBridgeClient::Stop() {
  if (!m_running.exchange(false))
    return;
  {
    std::lock_guard<std::mutex> lock(m_pipe_mutex);
    if (m_pipe != INVALID_HANDLE_VALUE) {
      CancelIoEx(m_pipe, nullptr);
      CloseHandle(m_pipe);
      m_pipe = INVALID_HANDLE_VALUE;
    }
  }
  if (m_thread.joinable())
    m_thread.join();
}

void LangouBridgeClient::Run() {
  while (m_running.load()) {
    if (!WaitNamedPipeW(kPipePath, 1000)) {
      std::this_thread::sleep_for(std::chrono::milliseconds(300));
      continue;
    }

    HANDLE pipe = CreateFileW(kPipePath, GENERIC_READ | GENERIC_WRITE, 0, nullptr,
                              OPEN_EXISTING, 0, nullptr);
    if (pipe == INVALID_HANDLE_VALUE)
      continue;
    {
      std::lock_guard<std::mutex> lock(m_pipe_mutex);
      if (!m_running.load()) {
        CloseHandle(pipe);
        return;
      }
      m_pipe = pipe;
    }

    if (!WriteLine(pipe, LangouBridgeProtocol::Hello())) {
      std::lock_guard<std::mutex> lock(m_pipe_mutex);
      if (m_pipe == pipe) {
        CloseHandle(pipe);
        m_pipe = INVALID_HANDLE_VALUE;
      }
      continue;
    }

    std::string pending;
    char buffer[2048];
    DWORD bytes_read = 0;
    while (m_running.load() &&
           ReadFile(pipe, buffer, sizeof(buffer), &bytes_read, nullptr) &&
           bytes_read > 0) {
      pending.append(buffer, bytes_read);
      if (pending.size() > 16384) {
        pending.clear();
        break;
      }
      size_t newline = 0;
      while ((newline = pending.find('\n')) != std::string::npos) {
        std::string line = pending.substr(0, newline);
        if (!line.empty() && line.back() == '\r')
          line.pop_back();
        pending.erase(0, newline + 1);
        HandleLine(line, pipe);
      }
    }

    std::lock_guard<std::mutex> lock(m_pipe_mutex);
    if (m_pipe == pipe) {
      CloseHandle(pipe);
      m_pipe = INVALID_HANDLE_VALUE;
    }
  }
}

void LangouBridgeClient::HandleLine(const std::string& line, HANDLE pipe) {
  LangouCommitCommand command;
  if (LangouBridgeProtocol::ParseCommitText(line, &command)) {
    const bool accepted =
        m_commit_handler(command.request_id, command.text);
    WriteLine(
        pipe,
        LangouBridgeProtocol::Acknowledgement(command.request_id, accepted));
    return;
  }
  LangouThemeCommand theme;
  if (LangouBridgeProtocol::ParseSetTheme(line, &theme))
    m_theme_handler(theme.theme);
}

bool LangouBridgeClient::WriteLine(HANDLE pipe, const std::string& line) {
  if (line.empty())
    return false;
  const std::string payload = line + '\n';
  DWORD written = 0;
  return WriteFile(pipe, payload.data(), static_cast<DWORD>(payload.size()),
                   &written, nullptr) &&
         written == static_cast<DWORD>(payload.size());
}
