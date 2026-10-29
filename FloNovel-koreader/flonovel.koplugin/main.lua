local WidgetContainer = require("ui/widget/container/widgetcontainer")
local Dispatcher = require("dispatcher")
local Event = require("ui/event")
local UIManager = require("ui/uimanager")
local InfoMessage = require("ui/widget/infomessage")
local Map = require("flonovel.position_map")
local Chapters = require("flonovel.chapters")
local tr = require("flonovel.strings")
local State = require("flonovel.state")
local Paths = require("flonovel.paths")
local Position = require("flonovel.reading_position")
local Dropbox = require("flonovel.dropbox")
local FastPosition = require("flonovel.fast_position")

local FloNovel = WidgetContainer:extend{ name = "flonovel" }

function FloNovel:init()
    self.options = G_reader_settings:readSetting("flonovel") or { chapters = true, divisions = 4 }
    self.options.divisions = self.options.divisions or 4
    self.generation = 0
    self.checkpoint = function() self:checkpointPosition(true) end
    self.initial_fetch = function() self:fetchPosition() end
    self.close_flush = function() self:flushPending(false) end
    local actions = {
        FloNovelNextChapter = "next_chapter", FloNovelPreviousChapter = "previous_chapter",
        FloNovelNextPart = "next_part", FloNovelPreviousPart = "previous_part",
        FloNovelChapters = "list",
    }
    for event, key in pairs(actions) do
        Dispatcher:registerAction("flonovel_" .. key, {
            category = "none", event = event, title = "FloNovel: " .. tr(key), reader = true,
        })
    end
    self.ui.menu:registerToMainMenu(self)
    local config, directory = self:loadConnection()
    if config and config.fast_position_enabled then
        State.write(directory .. "/fast-backend.json", {
            version = 1, backend = "utf8proc", supported = FastPosition.supported,
        })
    end
end

function FloNovel:notice(reason)
    self.last_error = reason
    UIManager:show(InfoMessage:new{ text = tr("unavailable", reason), timeout = 5 })
end

function FloNovel:invalidate()
    self.generation = self.generation + 1
    self.map, self.points, self.chapter_list = nil, nil, nil
end

function FloNovel:ensureMap()
    if self.map then return self.map end
    local doc = self.ui.document
    if not doc or type(doc.file) ~= "string" or not doc.file:lower():match("%.txt$")
        or not self.ui.rolling then return nil, tr("not_txt") end
    if self.ui.typeset and self.ui.typeset.txt_preformatted ~= 1 then
        return nil, tr("preformatted")
    end
    local logger, clock = require("logger"), require("socket").gettime
    local started, previous, previous_cpu = clock(), clock(), os.clock()
    local function phase(name)
        local now, cpu = clock(), os.clock()
        logger.info(string.format("FloNovel preparation %s wall=%.3fs cpu=%.3fs", name, now - previous, cpu - previous_cpu))
        previous, previous_cpu = now, cpu
    end
    local hash = require("flonovel.content_hash")
    logger.info("FloNovel preparation start; hash=" .. hash.backend)
    local file = io.open(doc.file, "rb")
    if not file then return nil, "source_unreadable" end
    -- Bound allocation on memory-constrained Kindles; never truncate a mapped book.
    local source = file:read(16 * 1024 * 1024 + 1)
    file:close()
    source = source or ""
    if #source > 16 * 1024 * 1024 then return nil, tr("too_large") end
    phase("read")
    local ok, map, reason = pcall(Map.build, doc, source, { known_line_changes = true })
    if not ok then return nil, "mapping_failed" end
    if not map then return nil, reason end
    phase("map")
    local points, chapters = Chapters.breakpoints(source, self.options.divisions)
    if not points then return nil, chapters end
    phase("chapters")
    map.source_hash = hash.text(source)
    phase("hash_memory")
    if hash.file(doc.file) ~= map.source_hash then return nil, "source_changed_during_mapping" end
    phase("hash_file")
    logger.info(string.format("FloNovel preparation complete wall=%.3fs", clock() - started))
    self.map, self.points, self.chapter_list = map, points, chapters
    return map
end

function FloNovel:gotoOffset(offset)
    local map, reason = self:ensureMap()
    if not map then self:notice(reason); return true end
    local xp, failure = map:to_exact_boundary(offset)
    if not xp then self:notice(failure); return true end
    self.last_error = nil
    self.ui:handleEvent(Event:new("GotoXPointer", xp))
    return true
end

function FloNovel:navigate(forward, subdivisions)
    if not self.options.chapters then self:notice(tr("disabled")); return true end
    local map, reason = self:ensureMap()
    if not map then self:notice(reason); return true end
    local current, failure = map:from_xpointer(self.ui.rolling:getLastProgress())
    if not current then self:notice(failure); return true end
    local points = self.points
    if not subdivisions then
        points = {}
        for _, chapter in ipairs(self.chapter_list) do points[#points + 1] = chapter.offset end
    end
    local target, missing = Chapters.target(points, current, forward)
    if not target then self:notice(missing); return true end
    return self:gotoOffset(target)
end

function FloNovel:onFloNovelNextChapter() return self:navigate(true, false) end
function FloNovel:onFloNovelPreviousChapter() return self:navigate(false, false) end
function FloNovel:onFloNovelNextPart() return self:navigate(true, true) end
function FloNovel:onFloNovelPreviousPart() return self:navigate(false, true) end

function FloNovel:onFloNovelChapters()
    if not self.options.chapters then self:notice(tr("disabled")); return true end
    local map, reason = self:ensureMap()
    if not map then self:notice(reason); return true end
    if #self.chapter_list == 0 then self:notice(tr("no_chapters")); return true end
    local items, generation = {}, self.generation
    local menu
    for _, chapter in ipairs(self.chapter_list) do
        local offset = chapter.offset
        items[#items + 1] = { text = chapter.title, callback = function()
            UIManager:close(menu)
            if generation == self.generation and self.options.chapters then self:gotoOffset(offset) end
        end }
    end
    menu = require("ui/widget/menu"):new{ title = tr("list"), item_table = items,
        width = require("device").screen:getWidth() }
    UIManager:show(menu)
    return true
end

function FloNovel:saveOptions()
    G_reader_settings:saveSetting("flonovel", self.options)
end

function FloNovel:loadConnection()
    local directory, reason = State.directory()
    if not directory then return nil, reason end
    local config, failure = State.read(directory .. "/config.json")
    if not config then return nil, failure == "missing" and tr("setup") or failure end
    if config.version ~= 1 or type(config.account_id) ~= "string"
        or Paths.root(config.library_root) ~= config.library_root then return nil, "invalid_connection_or_library" end
    return config, directory
end

function FloNovel:message(text)
    UIManager:show(InfoMessage:new{ text = text, timeout = 6 })
end

function FloNovel:runNetwork(kind, task, callback, interactive)
    if self.network_busy then if interactive then self:message(tr("busy")) end; return end
    if kind and not self.options[kind] then return end
    if not interactive and not require("ui/network/manager"):isConnected() then return end
    self.network_busy = true
    local generation = self.generation
    local Trapper = require("ui/trapper")
    Trapper:wrap(function()
        local complete, result, reason = Trapper:dismissableRunInSubprocess(function()
            local ok, value, failure = pcall(task)
            if not ok then return nil, "synchronization_failed" end
            return value, failure
        end, interactive and tr("syncing") or nil)
        self.network_busy = nil
        if kind and not self.options[kind] then return end
        if kind == "position" and generation ~= self.generation then return end
        if not complete then if interactive then self:message(tr("cancelled")) end; return end
        if not result then
            self.sync_error = reason
            if interactive then self:message(tr("network_failed", reason)) end
            return
        end
        self.sync_error = nil
        callback(result, generation)
    end)
end

function FloNovel:checkConnection()
    local config, directory = self:loadConnection()
    if not config then self:message(tr("network_failed", directory)); return end
    self:runNetwork(nil, function()
        local client = Dropbox.new(config)
        local ok, reason = client:refresh()
        if not ok then return nil, reason end
        local secret, failure = client:secret(config.dev == true)
        if not secret then return nil, failure end
        config.shared_secret = secret
        config.position_verified = false
        if config.supabase_url and config.publishable_key then
            ok, reason = Position.new(config):verify()
            if not ok then return nil, reason end
            config.position_verified = true
        end
        local saved, save_reason = State.write(directory .. "/config.json", config)
        if not saved then return nil, save_reason end
        return true
    end, function() self:message(tr("connected")) end, true)
end

function FloNovel:syncFiles(confirmation)
    local config, directory = self:loadConnection()
    if not config then self:message(tr("network_failed", directory)); return end
    local open_file = self.ui.document and self.ui.document.file
    if open_file then open_file = require("ffi/util").realpath(open_file) or open_file end
    self:runNetwork("files", function()
        local client = Dropbox.new(config)
        local ok, reason = client:refresh()
        if not ok then return nil, reason end
        return client:sync(directory .. "/sync.json", config.library_root, open_file, confirmation)
    end, function(report)
        if report.requires_confirmation then
            UIManager:show(require("ui/widget/confirmbox"):new{
                text = tr("deletion_confirm", report.reason, report.deletion_count),
                ok_callback = function() if self.options.files then self:syncFiles(report.signature) end end,
            })
            return
        end
        self:message(tr("files_done", report.downloaded, report.deleted_remote, report.recovered, #report.deferred))
        self.file_report = report
        if #report.deferred > 0 then self.sync_error = report.deferred[1].reason end
    end, true)
end

function FloNovel:bookIdentity(config)
    local doc = self.ui.document
    local physical = doc and require("ffi/util").realpath(doc.file)
    local prefix = config.library_root .. "/"
    if not physical or physical:sub(1, #prefix) ~= prefix then return nil, "book_outside_library" end
    local relative = physical:sub(#prefix + 1)
    local checked, reason = Paths.resolve(config.library_root, relative)
    if not checked then return nil, reason end
    local key, failure = Paths.key(relative)
    if not key then return nil, failure end
    return { relative = relative, key = key, physical = physical }
end

function FloNovel:connectionMatches(config)
    local live = self:loadConnection()
    return live and live.library_root == config.library_root and Position.scope(live) == Position.scope(config)
end

function FloNovel:checkpointPosition(send, interactive)
    if not self.options.position then return end
    if not self.ui.document then if send then self:flushPending(interactive) end; return end
    local config, directory = self:loadConnection()
    if not config then if interactive then self:message(tr("network_failed", directory)) end; return end
    local identity, reason = self:bookIdentity(config)
    local map, map_reason = self:ensureMap()
    local offset, failure
    if identity and map then offset, failure = map:from_xpointer(self.ui.rolling:getLastProgress()) end
    if offset == nil then
        self.sync_error = reason or map_reason or failure
        if interactive then self:notice(self.sync_error) end
        return
    end
    local client = Position.new(config)
    local ok, queue_reason = client:enqueue(directory .. "/positions.json", identity.relative, offset, map.source_hash)
    if not ok then self.sync_error = queue_reason; self:message(tr("network_failed", queue_reason)); return end
    if send then
        self:runNetwork("position", function() return client:flush(directory .. "/positions.json") end,
            function(report)
                if #report.deferred > 0 then self.sync_error = report.deferred[1] end
                if interactive then self:message(tr("position_done", report.sent, #report.deferred)) end
            end, interactive)
    end
end

function FloNovel:flushPending(interactive)
    if not self.options.position then return end
    local config, directory = self:loadConnection()
    if not config then if interactive then self:message(tr("network_failed", directory)) end; return end
    self:runNetwork("position", function() return Position.new(config):flush(directory .. "/positions.json") end,
        function(report)
            if #report.deferred > 0 then self.sync_error = report.deferred[1] end
            if interactive then self:message(tr("position_done", report.sent, #report.deferred)) end
        end, interactive)
end

function FloNovel:fetchPosition()
    if not self.options.position then return end
    local config = self:loadConnection()
    if not config or not config.position_verified then return end
    local identity = self:bookIdentity(config)
    if not identity then return end
    -- Most newly downloaded books have no remote position. Do not scan and
    -- hash the whole book before an absent/offline lookup can be dismissed.
    self:runNetwork("position", function() return Position.new(config):fetch(identity.key) end,
        function(remote, generation)
            if generation ~= self.generation or remote.absent or not self:connectionMatches(config) then return end
            local map = self:ensureMap()
            if not map then return end
            local current = map:from_xpointer(self.ui.rolling:getLastProgress())
            if not current or remote.char_offset - current <= 500
                or require("flonovel.content_hash").file(identity.physical) ~= map.source_hash then return end
            local xp = map:to_exact_boundary(remote.char_offset)
            if not xp then self.sync_error = "remote_position_not_representable"; return end
            UIManager:show(require("ui/widget/confirmbox"):new{
                text = tr("remote_position", remote.char_offset),
                ok_callback = function()
                    if generation == self.generation and self.options.position and self.map == map
                        and self:connectionMatches(config)
                        and require("flonovel.content_hash").file(identity.physical) == map.source_hash then
                        self:gotoOffset(remote.char_offset)
                    end
                end,
            })
        end, false)
end

function FloNovel:resetScope()
    UIManager:show(require("ui/widget/confirmbox"):new{ text = tr("reset_confirm"), ok_callback = function()
        if self.network_busy then self:message(tr("busy")); return end
        local config, directory = self:loadConnection()
        if not config then self:message(tr("network_failed", directory)); return end
        local ok, reason = State.write(directory .. "/sync.json", {
            version = 1, account = config.account_id, scope = Dropbox.scope(config), root = config.library_root, files = {},
        })
        if ok then ok, reason = State.write(directory .. "/positions.json", {
            version = 1, account = config.account_id, scope = Position.scope(config), root = config.library_root, jobs = {},
        }) end
        self:invalidate()
        if not ok then self:message(tr("network_failed", reason)) end
    end })
end

function FloNovel:addToMainMenu(items)
    local entries = {
        { text = tr("chapters"), checked_func = function() return self.options.chapters end,
          callback = function()
              self.options.chapters = not self.options.chapters
              self:invalidate()
              self:saveOptions()
          end },
        { text = tr("list"), callback = function() self:onFloNovelChapters() end },
        { text = tr("next_chapter"), callback = function() self:onFloNovelNextChapter() end },
        { text = tr("previous_chapter"), callback = function() self:onFloNovelPreviousChapter() end },
        { text = tr("next_part"), callback = function() self:onFloNovelNextPart() end },
        { text = tr("previous_part"), callback = function() self:onFloNovelPreviousPart() end },
    }
    for _, kind in ipairs{ "files", "position" } do
        entries[#entries + 1] = { text = tr(kind), checked_func = function() return self.options[kind] == true end,
            callback = function()
                self.options[kind] = not self.options[kind]
                self:invalidate()
                self:saveOptions()
                if self.options.position then UIManager:scheduleIn(1, self.initial_fetch) end
            end }
    end
    entries[#entries + 1] = { text = tr("fast_position"), checked_func = function()
        local config = self:loadConnection()
        return config and config.fast_position_enabled == true
    end, callback = function()
        local config, directory = self:loadConnection()
        if not config then self:message(tr("setup")); return end
        config.fast_position_enabled = not config.fast_position_enabled
        local ok, reason = State.write(directory .. "/config.json", config)
        if not ok then self:message(tr("network_failed", reason)); return end
        self:invalidate()
        if config.fast_position_enabled and not FastPosition.supported then self:notice("fast_native_unavailable") end
        if config.fast_position_enabled and self.options.position then UIManager:scheduleIn(1, self.initial_fetch) end
    end }
    entries[#entries + 1] = { text = tr("connect"), callback = function() self:checkConnection() end }
    entries[#entries + 1] = { text = tr("sync_files"), enabled_func = function() return self.options.files == true end,
        callback = function() self:syncFiles() end }
    entries[#entries + 1] = { text = tr("sync_position"), enabled_func = function() return self.options.position == true end,
        callback = function() self:checkpointPosition(true, true) end }
    entries[#entries + 1] = { text = tr("reset_scope"), callback = function() self:resetScope() end }
    entries[#entries + 1] = { text = tr("status"), callback = function()
        self:message(self.sync_error and tr("network_failed", self.sync_error) or tr("status_ok"))
    end }
    local divisions = {}
    for _, count in ipairs{ 1, 2, 4, 8, 16, 32 } do
        divisions[#divisions + 1] = { text = tostring(count),
            checked_func = function() return self.options.divisions == count end,
            callback = function()
                self.options.divisions = count
                self:invalidate()
                self:saveOptions()
            end }
    end
    entries[#entries + 1] = { text = tr("divisions"), sub_item_table = divisions }
    -- Orphan hints append to the end of a tab, which can hide this behind a
    -- second page. Put the entry first in the standard reader/file-manager order.
    local order = require(self.ui.document and "ui/elements/reader_menu_order"
        or "ui/elements/filemanager_menu_order")
    if order.tools then
        local found = false
        for _, id in ipairs(order.tools) do if id == "flonovel" then found = true; break end end
        if not found then table.insert(order.tools, 1, "flonovel") end
    end
    items.flonovel = { text = "FloNovel", sorting_hint = "tools", sub_item_table = entries }
end

function FloNovel:onReaderReady()
    self:invalidate()
    if self.options.position then UIManager:scheduleIn(1, self.initial_fetch) end
end
function FloNovel:onCloseDocument()
    UIManager:unschedule(self.checkpoint)
    UIManager:unschedule(self.initial_fetch)
    self:checkpointPosition(false)
    self:invalidate()
    if self.options.position then UIManager:scheduleIn(0, self.close_flush) end
end
function FloNovel:onDocumentRerendered() self:invalidate() end
function FloNovel:onPageUpdate()
    if self.options.position then
        UIManager:unschedule(self.checkpoint)
        UIManager:scheduleIn(10, self.checkpoint)
    end
end
function FloNovel:onSuspend() self:checkpointPosition(false) end
function FloNovel:onResume()
    if self.options.position then UIManager:scheduleIn(1, self.initial_fetch) end
end

FastPosition.install(FloNovel)
return FloNovel
