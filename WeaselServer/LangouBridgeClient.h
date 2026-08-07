#pragma once

#include <Windows.h>
#include <atomic>
#include <functional>
#include <mutex>
#include <string>
#include <thread>

class LangouBridgeClient {
 public:
  using CommitHandler =
      std::function<bool(const std::string& request_id, const std::string& text)>;
  using ThemeHandler = std::function<bool(const std::string& theme)>;

  LangouBridgeClient(CommitHandler commit_handler, ThemeHandler theme_handler);
  ~LangouBridgeClient();

  void Start();
  void Stop();

 private:
  void Run();
  void HandleLine(const std::string& line, HANDLE pipe);
  static bool WriteLine(HANDLE pipe, const std::string& line);

  CommitHandler m_commit_handler;
  ThemeHandler m_theme_handler;
  std::atomic_bool m_running{false};
  std::mutex m_pipe_mutex;
  HANDLE m_pipe = INVALID_HANDLE_VALUE;
  std::thread m_thread;
};
