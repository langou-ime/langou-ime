#include "stdafx.h"
#include "WeaselServerApp.h"
#include <filesystem>

WeaselServerApp::WeaselServerApp()
    : tray_icon(m_ui),
      m_handler(std::make_unique<RimeWithWeaselHandler>(&m_ui)),
      m_langou_bridge(std::make_unique<LangouBridgeClient>(
          [this](const std::string& request_id, const std::string& text) {
            return m_handler->QueueExternalCommitText(request_id, text);
          },
          [this](const std::string& theme) {
            const UINT command = theme == "cream"
                                     ? ID_LANGOU_THEME_CREAM
                                     : theme == "soda" ? ID_LANGOU_THEME_SODA
                                                       : ID_LANGOU_THEME_MOON;
            return PostMessage(m_server.GetHWnd(), WM_COMMAND, command, 0) !=
                   FALSE;
          })) {
  // m_handler.reset(new RimeWithWeaselHandler(&m_ui));
  m_server.SetRequestHandler(m_handler.get());
  SetupMenuHandlers();
}

WeaselServerApp::~WeaselServerApp() {}

int WeaselServerApp::Run() {
  if (!m_server.Start())
    return -1;

  m_ui.Create(m_server.GetHWnd());

  m_handler->Initialize();
  m_handler->OnUpdateUI([this]() { tray_icon.Refresh(); });
  execute(install_dir() / L"LangouAssistant.exe", L"/background");
  m_langou_bridge->Start();

  tray_icon.Create(m_server.GetHWnd());
  tray_icon.Refresh();

  int ret = m_server.Run();

  m_langou_bridge->Stop();
  m_handler->Finalize();
  m_ui.Destroy();
  tray_icon.RemoveIcon();
  return ret;
}

void WeaselServerApp::SetupMenuHandlers() {
  std::filesystem::path dir = install_dir();
  m_server.AddMenuHandler(ID_LANGOU_THEME_CREAM, [this] {
    m_handler->ApplyLangouTheme("cream");
    return true;
  });
  m_server.AddMenuHandler(ID_LANGOU_THEME_SODA, [this] {
    m_handler->ApplyLangouTheme("soda");
    return true;
  });
  m_server.AddMenuHandler(ID_LANGOU_THEME_MOON, [this] {
    m_handler->ApplyLangouTheme("moon");
    return true;
  });
  m_server.AddMenuHandler(ID_WEASELTRAY_QUIT,
                          [this] { return m_server.Stop() == 0; });
  m_server.AddMenuHandler(ID_WEASELTRAY_DEPLOY,
                          std::bind(execute, dir / L"WeaselDeployer.exe",
                                    std::wstring(L"/deploy")));
  m_server.AddMenuHandler(
      ID_WEASELTRAY_SETTINGS,
      std::bind(execute, dir / L"LangouAssistant.exe",
                std::wstring(L"/settings")));
  m_server.AddMenuHandler(
      ID_WEASELTRAY_DICT_MANAGEMENT,
      std::bind(execute, dir / L"WeaselDeployer.exe", std::wstring(L"/dict")));
  m_server.AddMenuHandler(
      ID_WEASELTRAY_SYNC,
      std::bind(execute, dir / L"WeaselDeployer.exe", std::wstring(L"/sync")));
  m_server.AddMenuHandler(ID_WEASELTRAY_WIKI,
                          std::bind(open, L"https://langou.tech/zh/guide.html"));
  m_server.AddMenuHandler(ID_WEASELTRAY_HOMEPAGE,
                          std::bind(open, L"https://langou.tech/"));
  m_server.AddMenuHandler(ID_WEASELTRAY_FORUM,
                          std::bind(open,
                                    L"https://langou.tech/zh/community.html"));
  m_server.AddMenuHandler(
      ID_WEASELTRAY_CHECKUPDATE,
      std::bind(execute, dir / L"LangouAssistant.exe",
                std::wstring(L"/update")));
  m_server.AddMenuHandler(ID_WEASELTRAY_INSTALLDIR, std::bind(explore, dir));
  m_server.AddMenuHandler(ID_WEASELTRAY_USERCONFIG,
                          std::bind(explore, WeaselUserDataPath()));
  m_server.AddMenuHandler(ID_WEASELTRAY_LOGDIR,
                          std::bind(explore, WeaselLogPath()));
}
