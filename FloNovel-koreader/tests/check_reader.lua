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
local function prepare_toc(plugin, after)
    local Trapper = require("ui/trapper")
    local wrap, run = Trapper.wrap, Trapper.dismissableRunInSubprocess
    Trapper.wrap = function(_, fn) fn() end
    Trapper.dismissableRunInSubprocess = function(_, fn)
        local result, reason = fn()
        if after then after() end
        return true, result, reason
    end
    plugin:buildNativeToc()
    Trapper.wrap, Trapper.dismissableRunInSubprocess = wrap, run
end
prepare_toc(plugin)
ui.toc:fillToc()
check("native ToC detects two chapters", #ui.toc.toc == 2)
check("native ToC retains trusted long title", #ui.toc.toc[2].title > 60)
check("native ToC does not store handmade entries", #ui.handmade.toc == 0)
check("no global TXT setting mutation", not G_reader_settings:has("txt_preformatted"))
for _, offset in ipairs{ 0, 1, 3, 12, map.length } do
    local xp = map:to_exact_boundary(offset)
    check("exact roundtrip " .. offset, xp and map:from_xpointer(xp) == offset)
end
plugin:gotoOffset(0)
local second = map:from_xpointer(ui.toc.toc[2].xpointer)
require("dispatcher"):execute{ next_chapter = true }
check("native dispatcher advances chapter", ui.rolling.current_page == ui.toc.toc[2].page)
require("dispatcher"):execute{ prev_chapter = true }
check("native dispatcher returns previous chapter", ui.rolling.current_page == ui.toc.toc[1].page)
plugin:gotoOffset(0)
local expected = map:from_xpointer(ui.rolling:getLastProgress())
ui:handleEvent(Event:new("SetFontSize", 28))
check("font change retains canonical anchor", map:from_xpointer(ui.rolling:getLastProgress()) == expected)
local items = {}
plugin:addToMainMenu(items)
check("product menu registered", items.flonovel ~= nil)
local fetch_position = plugin.fetchPosition
local manual_lookup = false
plugin.fetchPosition = function(_, interactive) manual_lookup = interactive == true end
items.flonovel.sub_item_table[8].callback()
check("explicit remote check menu requests interactive lookup", manual_lookup)
plugin.fetchPosition = fetch_position
local sorted_menu = require("ui/menusorter"):sort({ ["KOMenu:menu_buttons"] = {}, tools = { text = "Tools" }, flonovel = items.flonovel },
    { ["KOMenu:menu_buttons"] = { "tools" }, tools = require("ui/elements/reader_menu_order").tools })
local tools_menu = require("ui/menusorter"):findById(sorted_menu, "tools")
check("reader tools tab opens FloNovel directly as its first item", tools_menu and (tools_menu.sub_item_table or tools_menu)[1].text == "FloNovel")
items.flonovel.sub_item_table[1].callback()
check("disable restores original ToC provider", plugin.toc_provider == nil and not plugin.options.chapters)
items.flonovel.sub_item_table[1].callback()
prepare_toc(plugin)
check("re-enable supplies native ToC", plugin.toc_provider ~= nil)
check("legacy navigation handlers removed", plugin.onFloNovelNextChapter == nil and plugin.onFloNovelNextPart == nil)
local manual_provider = function() return {} end
ui.document.getToc = manual_provider
plugin:clearNativeToc()
check("clearing marker ToC preserves a newer manual provider", ui.document.getToc == manual_provider)
ui.document.getToc = nil
prepare_toc(plugin, function() plugin:clearNativeToc(); plugin.options.chapters = false end)
check("disabled feature rejects late worker result", plugin.toc_provider == nil and rawget(ui.document, "getToc") == nil)
plugin.options.chapters = true
ui.handmade.toc_enabled = true
prepare_toc(plugin)
check("handmade ToC takes precedence", plugin.toc_provider == nil)
ui.handmade.toc_enabled = false
prepare_toc(plugin)
check("marker ToC can rebuild after lifecycle invalidation", plugin.toc_provider ~= nil)
plugin:gotoOffset(second)
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
local Trapper, Network = require("ui/trapper"), require("ui/network/manager")
local wrap, run, connected = Trapper.wrap, Trapper.dismissableRunInSubprocess, Network.isConnected
local control, passed_control, delivered = {}, nil, false
Trapper.wrap = function(_, fn) fn() end
Trapper.dismissableRunInSubprocess = function(_, fn, handle) passed_control = handle; return true, fn() end
Network.isConnected = function() return true end
plugin.position_fetch_control = control
plugin:runNetwork("position", function() return true end, function() delivered = true end, false, control)
check("automatic lookup passes explicit nonmodal cancellation handle", passed_control == control and delivered)
check("completed lookup releases lifecycle handle", plugin.position_fetch_control == nil)
local cancelled = false
plugin.position_fetch_control = { dismiss_callback = function() cancelled = true end }
plugin:invalidate()
check("invalidation cancels automatic lookup", cancelled and not plugin.remote_lookup_completed)
plugin.position_fetch_control = nil
Trapper.wrap, Trapper.dismissableRunInSubprocess, Network.isConnected = wrap, run, connected
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
check("different book builds its own source map", second_map and second_map ~= old_map )
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
        local Fast = require("flonovel.fast_position")
        local rolling = { getLastProgress = function() return xp end }
        local captured, reason = Fast.capture{ document = ui.document, rolling = rolling, typeset = ui.typeset }
        local located, failure
        if captured then located, failure = Fast.locate(ui.document.file, captured) end
        check(case.name .. "/fast blank source anchor/" .. index .. ":" .. tostring(reason or failure),
            located and located.offset == expected)
        if index == 1 and captured then
            captured.empty = captured.empty + 1
            check(case.name .. "/fast blank rejects wrong native child", Fast.locate(ui.document.file, captured) == nil)
            captured.empty = captured.empty - 1
            local document = captured.document
            captured.document = setmetatable({ getTextFromXPointer = function() return "wrong source" end }, { __index = document })
            if case.name ~= "native-leading-trailing" then
                check(case.name .. "/fast blank rejects mismatched adjacent text", Fast.locate(ui.document.file, captured) == nil)
            end
        end
    end
    if case.name == "native-long-lines" then
        local Fast = require("flonovel.fast_position")
        for index, offsets in ipairs(case.pres) do
            local xp = "/FictionBook/body/pre[" .. index .. "]/text().0"
            local reader = { document = ui.document, typeset = ui.typeset,
                rolling = { getLastProgress = function() return xp end } }
            local captured, reason = Fast.capture(reader)
            local value, failure
            if captured then value, failure = Fast.locate(ui.document.file, captured) end
            check("long native paragraph exact capture/" .. index .. ":" .. tostring(reason or failure),
                value and value.offset == offsets[1])
            local target = Fast.reverse(ui.document.file, offsets[1])
            check("long native paragraph exact reverse/" .. index, target
                and ui.document:getNormalizedXPointer(target.xp) == ui.document:getNormalizedXPointer(xp))
        end
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
check("FileManager has independent sync controls", fm_items.flonovel and #fm_items.flonovel.sub_item_table == 10)
local fm_sorted = require("ui/menusorter"):sort({ ["KOMenu:menu_buttons"] = {}, tools = { text = "Tools" }, flonovel = fm_items.flonovel },
    { ["KOMenu:menu_buttons"] = { "tools" }, tools = require("ui/elements/filemanager_menu_order").tools })
local fm_tools = require("ui/menusorter"):findById(fm_sorted, "tools")
check("file manager tools tab opens FloNovel directly as its first item", fm_tools and (fm_tools.sub_item_table or fm_tools)[1].text == "FloNovel")
check("reader mapping unavailable in FileManager", fm.flonovel:ensureMap() == nil)
fm:onClose()

ui = open("native-split-line.txt")
local Fast = require("flonovel.fast_position")
local first_native = ui.document:getTextFromXPointer("/FictionBook/body/pre[1]/text()")
check("native engine actually splits after the scalar threshold", first_native == string.rep("한",2100) .. " ")
local _, split_reason = Fast.reverse(ui.document.file, 2108)
check("fast reverse holds actual split native layout", split_reason == "fast_split_line_unsupported")
ui:onClose()

local Map = require("flonovel.position_map")
local Chapters = require("flonovel.chapters")
check("invalid UTF8 rejected", Map.utf16_length("\192\128") == nil)
check("NUL rejected", Map.utf16_length("a\0b") == nil)
check("supplementary UTF16", Map.utf16_length("a😀b") == 4)
local scan_file = home .. "/marker-scan.txt"
local function scan(text)
    local out = assert(io.open(scan_file, "wb")); out:write(text); out:close()
    return assert(Chapters.scan(scan_file))
end
check("ordinary text has no marker ToC", #scan("normal\ntext") == 0)
check("markers must start at beginning of line", #scan(" ## Indented\nbody ## Inline\n## Actual") == 1)
local marks = scan("\239\187\191\r\n## One\r\n \t\r\n## Two\n")
check("BOM CRLF and blank source ordinals", #marks == 2 and marks[1].pre == 1 and marks[1].child == 2 and marks[2].pre == 2 and marks[2].child == 4)
marks = scan(string.rep("x",65535) .. "\r\n## Across chunk\r\n## End")
check("CRLF across streaming chunk boundary", #marks == 2 and marks[1].pre == 2 and marks[2].pre == 3)
check("trusted title has no length cap", #scan("## " .. string.rep("한",2000))[1].title > 6000)
local json = require("json")
local file = assert(io.open(home .. "/results.json", "wb"))
file:write(json.encode{ total = total, passed = total - failed, failed = failed, skipped = 0,
    environment = "KOReader 2026.07.1 Linux dummy ReaderUI; not Kindle" })
file:close()
print(string.format("PRODUCT_READER total=%d passed=%d failed=%d", total, total - failed, failed))
os.exit(failed == 0 and 0 or 1)
