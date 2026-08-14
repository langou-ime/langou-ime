#include "../WeaselServer/LangouBridgeProtocol.h"

#include <cassert>
#include <string>

int main() {
  LangouCommitCommand command;
  assert(LangouBridgeProtocol::ParseCommitText(
      R"({"version":1,"type":"commit_text","request_id":"req-1","text":"你好"})",
      &command));
  assert(command.request_id == "req-1");
  assert(command.text == "你好");

  assert(!LangouBridgeProtocol::ParseCommitText(
      R"({"version":1,"type":"send","request_id":"req-1","text":"你好"})",
      &command));
  assert(!LangouBridgeProtocol::ParseCommitText(
      R"({"version":1,"type":"commit_text","request_id":"req-1","text":"你好","send":true})",
      &command));
  assert(!LangouBridgeProtocol::ParseCommitText(
      R"({"version":2,"type":"commit_text","request_id":"req-1","text":"你好"})",
      &command));

  LangouThemeCommand theme;
  assert(LangouBridgeProtocol::ParseSetTheme(
      R"({"version":1,"type":"set_theme","theme":"cream"})", &theme));
  assert(!LangouBridgeProtocol::ParseSetTheme(
      R"({"version":1,"type":"set_theme","theme":"downloaded"})", &theme));

  const std::string oversized(
      LangouBridgeProtocol::kMaximumTextBytes + 1, 'x');
  const std::string oversized_json =
      R"({"version":1,"type":"commit_text","request_id":"req-1","text":")" +
      oversized + R"("})";
  assert(!LangouBridgeProtocol::ParseCommitText(oversized_json, &command));
  return 0;
}
