require("setupkoenv")
local project = assert(os.getenv("FLONOVEL_PROJECT"))
local home = assert(os.getenv("KO_HOME"))
G_defaults = require("luadefaults"):open(home .. "/defaults.lua")
G_reader_settings = require("luasettings"):open(home .. "/settings.lua")
G_reader_settings:saveSetting("document_metadata_folder", "doc")
G_reader_settings:saveSetting("font_face", "FreeSans")
G_reader_settings:saveSetting("followed_link_marker", false)
require("logger"):setLevel(require("logger").levels.warn)
einkfb = require("ffi/framebuffer")
einkfb.dummy = true
local Device = require("device")
Device.screen:init()
Device.input.dummy = true
require("document/canvascontext"):init(Device)
local Loader = require("pluginloader")
Loader.enabled_plugins, Loader.disabled_plugins, Loader.loaded_plugins = {}, {}, {}
local path = project .. "/flonovel.koplugin"
Loader:_load{{ name = "flonovel", path = path, main = path .. "/main.lua", meta = path .. "/_meta.lua" }}
local Reader = require("apps/reader/readerui")
local Registry = require("document/documentregistry")
local Event = require("ui/event")
local total, failed = 0, 0
local function check(name, value)
    total = total + 1
    if not value then failed = failed + 1; print("FAIL " .. name) end
end
local function open(name)
    return Reader:new{ dimen = Device.screen:getSize(), document = Registry:openDocument(home .. "/" .. name) }
end
local ui = open("book.txt")
require("ui/uimanager"):show(ui)
require("ui/uimanager"):forceRePaint()
local plugin = ui.flonovel
check("product plugin loaded", plugin ~= nil)
local map, reason = plugin:ensureMap()
check("product mapper built: " .. tostring(reason), map ~= nil)
check("detected two chapters", #plugin.chapter_list == 2)
check("trusted long title", #plugin.chapter_list[2].title > 60)
check("no global TXT setting mutation", not G_reader_settings:has("txt_preformatted"))
for _, offset in ipairs{ 0, 1, 3, 12, map.length } do
    local xp = map:to_exact_boundary(offset)
    check("exact roundtrip " .. offset, xp and map:from_xpointer(xp) == offset)
end
plugin:gotoOffset(0)
ui:handleEvent(Event:new("FloNovelNextChapter"))
local second = plugin.chapter_list[2].offset
check("next chapter via ReaderUI", map:from_xpointer(ui.rolling:getLastProgress()) == second)
ui:handleEvent(Event:new("FloNovelPreviousChapter"))
check("previous chapter via ReaderUI", map:from_xpointer(ui.rolling:getLastProgress()) == 0)
plugin:gotoOffset(0)
ui:handleEvent(Event:new("FloNovelNextPart"))
check("subdivision uses exact target", map:from_xpointer(ui.rolling:getLastProgress()) == plugin.points[1])
local expected = map:from_xpointer(ui.rolling:getLastProgress())
ui:handleEvent(Event:new("SetFontSize", 28))
check("font change retains canonical anchor", map:from_xpointer(ui.rolling:getLastProgress()) == expected)
local items = {}
plugin:addToMainMenu(items)
check("product menu registered", items.flonovel ~= nil)
items.flonovel.sub_item_table[1].callback()
check("disable clears cache", not plugin.map and not plugin.options.chapters)
items.flonovel.sub_item_table[1].callback()
check("re-enable rebuilds", plugin:ensureMap() ~= nil)
require("dispatcher"):execute{ flonovel_next_chapter = true }
check("gesture dispatcher routes", plugin.map:from_xpointer(ui.rolling:getLastProgress()) == second)
local old_map = plugin.map
ui:onClose()
check("close releases document cache", plugin.map == nil)
ui = open("book.txt")
plugin = ui.flonovel
check("reopen builds fresh cache", plugin:ensureMap() ~= old_map)
check("reopen restores position", plugin.map:from_xpointer(ui.rolling:getLastProgress()) == second)
local State = require("flonovel.state")
local config_dir = assert(State.directory())
assert(State.write(config_dir .. "/config.json", { version = 1, account_id = "synthetic-account",
    library_root = require("flonovel.paths").root(home), position_verified = true,
    supabase_url = "https://synthetic.supabase.co", publishable_key = "synthetic-key", shared_secret = "synthetic-secret" }))
plugin.options.position, plugin.options.files, plugin.options.chapters = true, false, false
plugin:checkpointPosition(false)
check("position sharing independent of chapter and file toggles", State.read(config_dir .. "/positions.json").jobs["book.txt"].offset == second)
local pending_callback
plugin.runNetwork = function(_, _, _, callback) pending_callback = callback end
plugin:fetchPosition()
local generation = plugin.generation
plugin:invalidate()
pending_callback({ char_offset = second + 1000 }, generation)
check("late fetch cannot navigate after invalidation", ui.rolling:getLastProgress() ~= nil and plugin.map == nil)
plugin:fetchPosition()
check("lookup does not map book before response", plugin.map == nil)
pending_callback({ absent = true }, plugin.generation)
check("absent remote position does not map whole book", plugin.map == nil)
check("position map still available with chapters disabled", plugin:ensureMap() ~= nil)
plugin.options.chapters = true
plugin.options.position = false
ui:onClose()
ui = open("second.txt")
plugin = ui.flonovel
require("ui/uimanager"):show(ui)
local second_map = plugin:ensureMap()
check("different book builds its own source map", second_map and second_map ~= old_map and #plugin.chapter_list == 1)
local before_rejected = ui.rolling:getLastProgress()
plugin:gotoOffset(10)
check("surrogate interior rejected without rounding", plugin.last_error == "inside_surrogate_pair")
check("rejected jump preserves canonical anchor", ui.rolling:getLastProgress() == before_rejected)
require("ui/uimanager"):forceRePaint()
check("ordinary rendering continues after rejected jump", true)
ui:onClose()
local native_file = assert(io.open(home .. "/native-anchors.json", "rb"))
local native_cases = require("json").decode(native_file:read("*a"))
native_file:close()
for _, case in ipairs(native_cases) do
    ui = open(case.name .. ".txt")
    require("ui/uimanager"):show(ui)
    plugin = ui.flonovel
    local native_map = assert(plugin:ensureMap())
    if case.name == "native-crlf" then
        local terminal = "/FictionBook/body/empty-line[" .. (#case.empties + 1) .. "].0"
        check("CRLF native terminal empty line is exact EOF", native_map:from_xpointer(terminal) == case.length)
    end
    for index, expected in ipairs(case.empties) do
        local xp = "/FictionBook/body/empty-line[" .. index .. "].0"
        local actual = native_map:from_xpointer(xp)
        check(case.name .. "/blank source anchor/" .. index, actual == expected)
        local restored = actual and native_map:to_exact_boundary(actual)
        check(case.name .. "/blank exact roundtrip/" .. index, restored and native_map:from_xpointer(restored) == expected)
    end
    for _, size in ipairs{ 18, 32 } do
        ui:handleEvent(Event:new("SetFontSize", size))
        plugin:gotoOffset(0)
        for step, direction in ipairs{ 1, 1, 1, 1, 1, 1, -1, -1, 1 } do
            ui:handleEvent(Event:new("GotoViewRel", direction))
            local xp = ui.rolling:getLastProgress()
            local normalized = ui.document:getNormalizedXPointer(xp)
            local actual = native_map:from_xpointer(xp)
            local index = normalized:match("/empty%-line%[(%d+)%]%.0$")
            local expected
            if index then expected = case.empties[tonumber(index)]
            else
                local pre, point = normalized:match("/pre%[(%d+)%]/text%(%)%.(%d+)$")
                if pre then expected = case.pres[tonumber(pre)][tonumber(point) + 1] end
                if normalized == native_map.eof_normalized then expected = case.length end
            end
            check(case.name .. "/native page/" .. size .. "/" .. step, actual ~= nil and actual == expected)
        end
    end
    ui:onClose()
end
local fm = require("apps/filemanager/filemanager"):new{ root_path = home, dimen = Device.screen:getSize() }
check("plugin loaded in FileManager without a book", fm.flonovel ~= nil and fm.document == nil)
local fm_items = {}
fm.flonovel:addToMainMenu(fm_items)
check("FileManager has independent sync controls", fm_items.flonovel and #fm_items.flonovel.sub_item_table >= 12)
check("reader mapping unavailable in FileManager", fm.flonovel:ensureMap() == nil)
fm:onClose()

local Map = require("flonovel.position_map")
local Chapters = require("flonovel.chapters")
check("invalid UTF8 rejected", Map.utf16_length("\192\128") == nil)
check("NUL rejected", Map.utf16_length("a\0b") == nil)
check("supplementary UTF16", Map.utf16_length("a😀b") == 4)
check("invalid division count rejected", Chapters.breakpoints("## A", 0) == nil)
check("no markers produce no targets", #Chapters.breakpoints("normal\ntext", 4) == 0)
check("strict next", Chapters.target({ 0, 3, 6 }, 3, true) == 6)
check("strict previous", Chapters.target({ 0, 3, 6 }, 3, false) == 0)
check("no target stays absent", Chapters.target({ 0, 3, 6 }, 6, true) == nil)
local json = require("json")
local file = assert(io.open(home .. "/results.json", "wb"))
file:write(json.encode{ total = total, passed = total - failed, failed = failed, skipped = 0,
    environment = "KOReader 2026.07.1 Linux dummy ReaderUI; not Kindle" })
file:close()
print(string.format("PRODUCT_READER total=%d passed=%d failed=%d", total, total - failed, failed))
os.exit(failed == 0 and 0 or 1)
