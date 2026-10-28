-- Exercise actual plugin loading, ReaderUI events, and rendering on a dummy screen.
require("setupkoenv")
local json = require("json")
local project = "../../../.."
local root = project .. "/.work/results/position-probe"
local settings = assert(os.getenv("KO_HOME"), "Use an isolated probe session")
local file = assert(io.open(root .. "/manifest.json", "rb"))
local manifest = json.decode(file:read("*a"))
file:close()
G_defaults = require("luadefaults"):open(settings .. "/defaults.lua")
G_reader_settings = require("luasettings"):open(settings .. "/settings.lua")
G_reader_settings:saveSetting("document_metadata_folder", "doc")
G_reader_settings:saveSetting("txt_preformatted", 1)
G_reader_settings:saveSetting("font_face", "FreeSans")
G_reader_settings:saveSetting("followed_link_marker", false)
require("logger"):setLevel(require("logger").levels.warn)
einkfb = require("ffi/framebuffer")
einkfb.dummy = true
local Device = require("device")
Device.screen:init()
Device.input.dummy = true
require("document/canvascontext"):init(Device)
local PluginLoader = require("pluginloader")
PluginLoader.enabled_plugins, PluginLoader.disabled_plugins, PluginLoader.loaded_plugins = {}, {}, {}
local plugin_path = project .. "/experiments/flonovel.koplugin"
PluginLoader:_load({ { name = "flonovel", path = plugin_path, main = plugin_path .. "/main.lua",
    meta = plugin_path .. "/_meta.lua" } })
local Registry = require("document/documentregistry")
local ReaderUI = require("apps/reader/readerui")
local UIManager = require("ui/uimanager")
local Event = require("ui/event")
local bulk_mode = os.getenv("FLONOVEL_PROBE_BULK") == "1"
local result_prefix = bulk_mode and "bulk-" or ""
local Map = dofile(project .. "/experiments/" .. (bulk_mode and "bulk_position_map.lua" or "txt_position_map.lua"))
local output = { tests = {}, scope = "PC_dummy_screen_actual_ReaderUI_not_Kindle" }
local function check(name, success, detail)
    output.tests[#output.tests + 1] = { name = name, passed = not not success, detail = detail }
end

local fixture = manifest.fixtures[1]
local ui = ReaderUI:new{
    dimen = Device.screen:getSize(),
    document = Registry:openDocument(settings .. "/inputs/" .. fixture.name .. ".txt"),
}
check("plugin_loaded", ui.flonovel ~= nil)
check("reader_ready_event", ui.flonovel and ui.flonovel.reader_ready)
local items = {}
ui.flonovel:addToMainMenu(items)
check("menu_registered", items.flonovel_probe ~= nil)
local map, err = Map.build(ui.document, fixture.text)
check("reader_dom_matches_source", map ~= nil, err)
local failures = {}
if map then
    for _, sample in ipairs(fixture.samples) do
        local xp = assert(map:to_xpointer(sample.offset))
        ui:handleEvent(Event:new("GotoXPointer", xp))
        ui:handleEvent(Event:new("FloNovelProbePosition"))
        local restored = map:from_xpointer(ui.flonovel.last_observation)
        if restored ~= sample.offset then
            failures[#failures + 1] = { expected = sample.offset, actual = restored }
        end
    end
end
check("reader_event_retains_requested_anchor", map and #failures == 0, failures)
output.position_samples = #fixture.samples
UIManager:show(ui)
UIManager:forceRePaint()
Device.screen:shot(root .. "/" .. result_prefix .. "reader-ui.png")
check("reader_paint_completed", true)
ui.flonovel.last_observation = nil
require("dispatcher"):execute({ flonovel_probe_position = true })
check("dispatcher_action_routes_to_plugin", ui.flonovel.last_observation == ui.rolling:getLastProgress())
local last_sample = fixture.samples[#fixture.samples]
ui:handleEvent(Event:new("SetFontSize", 28))
check("font_reflow_retains_reader_anchor", map and map:from_xpointer(ui.rolling:getLastProgress()) == last_sample.offset)
ui:onClose()
local reopened = ReaderUI:new{
    dimen = Device.screen:getSize(),
    document = Registry:openDocument(settings .. "/inputs/" .. fixture.name .. ".txt"),
}
local reopened_map = Map.build(reopened.document, fixture.text)
check("reopen_retains_reader_anchor", reopened_map and reopened_map:from_xpointer(reopened.rolling:getLastProgress()) == last_sample.offset)
reopened:onClose()
output.total, output.failed = #output.tests, 0
for _, result in ipairs(output.tests) do
    if not result.passed then output.failed = output.failed + 1 end
end
output.passed = output.total - output.failed
output.mapper = bulk_mode and "optional_bulk" or "reference"
file = assert(io.open(root .. "/" .. result_prefix .. "reader-ui-results.json", "wb"))
file:write(json.encode(output))
file:close()
print(string.format("READER_UI_CHECKS total=%d passed=%d failed=%d", output.total, output.passed, output.failed))
os.exit(output.failed == 0 and 0 or 1)
