-- Fault-injection checks through actual discovery, loader, and ReaderUI.
require("setupkoenv")
local json, lfs = require("json"), require("libs/libkoreader-lfs")
local session = assert(os.getenv("KO_HOME"), "Use an isolated KO_HOME")
G_defaults = require("luadefaults"):open(session .. "/defaults.lua")
G_reader_settings = require("luasettings"):open(session .. "/settings.reader.lua")
local disabled = { a2_disabled = true }
for directory in lfs.dir("plugins") do
    if directory:sub(-9) == ".koplugin" then disabled[directory:sub(1, -10)] = true end
end
G_reader_settings:saveSetting("plugins_disabled", disabled)
G_reader_settings:saveSetting("extra_plugin_paths", { session .. "/plugins" })
G_reader_settings:saveSetting("document_metadata_folder", "doc")
G_reader_settings:saveSetting("txt_preformatted", 1)
G_reader_settings:saveSetting("font_face", "FreeSans")
G_reader_settings:saveSetting("language", "C")
G_reader_settings:saveSetting("a2_sentinel", "keep-this-value")
G_reader_settings:flush()
require("logger"):setLevel(require("logger").levels.warn)
einkfb = require("ffi/framebuffer"); einkfb.dummy = true
local Device = require("device")
Device.screen:init(); Device.input.dummy = true
require("document/canvascontext"):init(Device)
local Loader = require("pluginloader")
local ReaderUI, Registry = require("apps/reader/readerui"), require("document/documentregistry")
local Event, UIManager = require("ui/event"), require("ui/uimanager")
local output = { tests = {}, scope = "PC_isolated_fault_injection_not_Kindle_or_full_process_restart" }
local function check(name, passed)
    output.tests[#output.tests + 1] = { name = name, passed = not not passed }
end
local function new_reader()
    return ReaderUI:new{dimen=Device.screen:getSize(), document=Registry:openDocument(session .. "/inputs/lifecycle.txt")}
end
local function reset_loader()
    Loader.enabled_plugins, Loader.disabled_plugins, Loader.loaded_plugins, Loader.all_plugins = nil, nil, nil, nil
end
local discovered = Loader:_discover()
local names = {}
for _, plugin in ipairs(discovered) do names[plugin.name] = plugin end
check("synthetic_plugin_discovered", names.a2_healthy ~= nil)
check("renamed_directory_not_discovered", names.a2_removed == nil and names["a2_removed.koplugin"] == nil)
check("disabled_plugin_loads_metadata_only", names.a2_disabled and names.a2_disabled.disabled
    and names.a2_disabled.main:match("/_meta.lua$") ~= nil)
local ui = new_reader()
check("reader_created_despite_plugin_failures", ui.document ~= nil and ui.rolling ~= nil)
check("healthy_plugin_instantiated", ui.a2_healthy ~= nil)
check("healthy_ready_after_other_ready_failure", ui.a2_healthy and ui.a2_healthy.ready)
check("syntax_error_not_instantiated", ui.a2_syntax_error == nil)
check("load_error_not_instantiated", ui.a2_load_error == nil)
check("init_error_not_instantiated", ui.a2_init_error == nil)
check("disabled_plugin_main_not_executed", _G.a2_disabled_executed == nil and ui.a2_disabled == nil)
local handler = ui.a2_handler_error
local ok, handled = pcall(function() return handler:onA2InjectedFault() end)
check("handler_sandbox_returns_false_on_error", handler ~= nil and ok and handled == false)
local event_ok = pcall(function() ui:handleEvent(Event:new("A2InjectedFault")) end)
check("reader_event_dispatch_survives_error", event_ok)
ui:handleEvent(Event:new("A2Healthy"))
check("healthy_event_after_fault", ui.a2_healthy and ui.a2_healthy.calls == 1)
local page = ui.rolling.current_page
ui:handleEvent(Event:new("GotoViewRel", 1))
check("ordinary_page_turn_after_fault", ui.rolling.current_page > page)
UIManager:show(ui); UIManager:forceRePaint()
check("reader_paint_after_fault", true)
local last_xp = ui.rolling:getLastProgress()
Loader.show_info = false
local menu = Loader:genPluginManagerSubItem()
local healthy_toggle
for _, group in ipairs(menu) do
    for _, item in ipairs(group.sub_item_table or {}) do
        if item.text == "a2_healthy.koplugin" then healthy_toggle = item end
    end
end
check("actual_plugin_manager_toggle_found", healthy_toggle ~= nil)
if healthy_toggle then healthy_toggle.callback() end
check("manager_toggle_records_disable", G_reader_settings:readSetting("plugins_disabled").a2_healthy == true)
ui:onClose()
check("close_clears_loader_instances", next(Loader.loaded_plugins) == nil)
local settings = require("luasettings"):open(session .. "/settings.reader.lua")
check("unrelated_settings_preserved", settings:readSetting("a2_sentinel") == "keep-this-value"
    and settings:readSetting("language") == "C" and settings:readSetting("txt_preformatted") == 1)
check("disable_setting_persisted", settings:readSetting("plugins_disabled").a2_healthy == true)
reset_loader()
local reopened = new_reader()
check("fresh_loader_honors_disabled_plugin", reopened.a2_healthy == nil)
check("reopen_retains_reader_position", reopened.rolling:getLastProgress() == last_xp)
reopened:onClose()
local flags = G_reader_settings:readSetting("plugins_disabled")
flags.a2_healthy = nil
G_reader_settings:saveSetting("plugins_disabled", flags)
reset_loader()
local enabled = new_reader()
check("reenabled_plugin_loads_again", enabled.a2_healthy and enabled.a2_healthy.ready)
enabled:onClose()
output.total, output.failed = #output.tests, 0
for _, result in ipairs(output.tests) do if not result.passed then output.failed = output.failed + 1 end end
output.passed = output.total - output.failed
local file = assert(io.open("../../../../.work/results/a2/lifecycle-results.json", "wb"))
file:write(json.encode(output)); file:close()
print(string.format("A2_READER_CHECKS total=%d passed=%d failed=%d", output.total, output.passed, output.failed))
os.exit(output.failed == 0 and 0 or 1)
