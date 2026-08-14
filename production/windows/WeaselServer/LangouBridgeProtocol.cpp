#include "LangouBridgeProtocol.h"

#include <boost/property_tree/json_parser.hpp>
#include <boost/property_tree/ptree.hpp>
#include <algorithm>
#include <cctype>
#include <set>
#include <sstream>
#include <utility>

namespace {
bool IsIdentifier(const std::string& value) {
  return !value.empty() && value.size() <= 128 &&
         std::all_of(value.begin(), value.end(), [](unsigned char character) {
           return std::isalnum(character) || character == '_' || character == '-';
         });
}
}  // namespace

bool LangouBridgeProtocol::ParseCommitText(const std::string& json,
                                           LangouCommitCommand* command) {
  if (!command || json.empty() || json.size() > 8192)
    return false;

  try {
    boost::property_tree::ptree root;
    std::istringstream stream(json);
    boost::property_tree::read_json(stream, root);

    static const std::set<std::string> allowed_fields = {
        "version", "type", "request_id", "text"};
    std::set<std::string> observed_fields;
    for (const auto& field : root) {
      if (!allowed_fields.count(field.first) ||
          !observed_fields.insert(field.first).second)
        return false;
    }
    if (observed_fields != allowed_fields)
      return false;

    LangouCommitCommand parsed;
    parsed.version = root.get<int>("version");
    const auto type = root.get<std::string>("type");
    parsed.request_id = root.get<std::string>("request_id");
    parsed.text = root.get<std::string>("text");
    if (parsed.version != kVersion || type != "commit_text" ||
        !IsIdentifier(parsed.request_id) || parsed.text.empty() ||
        parsed.text.size() > kMaximumTextBytes ||
        parsed.text.find('\0') != std::string::npos)
      return false;

    *command = std::move(parsed);
    return true;
  } catch (const boost::property_tree::json_parser::json_parser_error&) {
    return false;
  } catch (const boost::property_tree::ptree_error&) {
    return false;
  }
}

bool LangouBridgeProtocol::ParseSetTheme(const std::string& json,
                                         LangouThemeCommand* command) {
  if (!command || json.empty() || json.size() > 512)
    return false;
  try {
    boost::property_tree::ptree root;
    std::istringstream stream(json);
    boost::property_tree::read_json(stream, root);
    static const std::set<std::string> allowed_fields = {
        "version", "type", "theme"};
    std::set<std::string> observed_fields;
    for (const auto& field : root) {
      if (!allowed_fields.count(field.first) ||
          !observed_fields.insert(field.first).second)
        return false;
    }
    if (observed_fields != allowed_fields ||
        root.get<int>("version") != kVersion ||
        root.get<std::string>("type") != "set_theme")
      return false;

    LangouThemeCommand parsed;
    parsed.version = kVersion;
    parsed.theme = root.get<std::string>("theme");
    if (parsed.theme != "cream" && parsed.theme != "soda" &&
        parsed.theme != "moon")
      return false;
    *command = std::move(parsed);
    return true;
  } catch (const boost::property_tree::json_parser::json_parser_error&) {
    return false;
  } catch (const boost::property_tree::ptree_error&) {
    return false;
  }
}

std::string LangouBridgeProtocol::Hello() {
  return "{\"version\":1,\"type\":\"hello\",\"app_version\":\"1.0.0\","
         "\"capabilities\":[\"commit_text\"]}";
}

std::string LangouBridgeProtocol::Acknowledgement(
    const std::string& request_id,
    bool accepted) {
  if (!IsIdentifier(request_id))
    return {};
  return "{\"version\":1,\"type\":\"ack\",\"request_id\":\"" + request_id +
         "\",\"accepted\":" + (accepted ? "true}" : "false}");
}
