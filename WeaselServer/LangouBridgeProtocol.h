#pragma once

#include <cstddef>
#include <string>

struct LangouCommitCommand {
  int version = 0;
  std::string request_id;
  std::string text;
};

struct LangouThemeCommand {
  int version = 0;
  std::string theme;
};

class LangouBridgeProtocol {
 public:
  static constexpr int kVersion = 1;
  static constexpr std::size_t kMaximumTextBytes = 4000;

  static bool ParseCommitText(const std::string& json,
                              LangouCommitCommand* command);
  static bool ParseSetTheme(const std::string& json,
                            LangouThemeCommand* command);
  static std::string Hello();
  static std::string Acknowledgement(const std::string& request_id,
                                     bool accepted);
};
