local WidgetContainer = require("ui/widget/container/widgetcontainer")
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
    self.options = G_reader_settings:readSetting("flonovel") or { chapters = true }
    self.options.divisions = nil
    self.generation = 0
    self.checkpoint = function() self:checkpointPosition(true) end
    self.initial_fetch = function() self:fetchPosition() end
    self.close_flush = function() self:flushPending(false) end
    self.prepare_toc = function() self:buildNativeToc() end
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
    self.map = nil
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
    map.source_hash = hash.text(source)
    phase("hash_memory")
    if hash.file(doc.file) ~= map.source_hash then return nil, "source_changed_during_mapping" end
    phase("hash_file")
    logger.info(string.format("FloNovel preparation complete wall=%.3fs", clock() - started))
    self.map = map
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

function FloNovel:clearNativeToc()
    self.chapter_generation = (self.chapter_generation or 0) + 1
    UIManager:unschedule(self.prepare_toc)
    local task = self.chapter_task
    if task and task.dismiss_callback then task.dismiss_callback() end
    if self.toc_document and self.toc_document.getToc == self.toc_provider then
        self.toc_document.getToc = self.original_toc_provider
        self.ui:handleEvent(Event:new("UpdateToc"))
    end
    self.toc_document, self.toc_provider, self.original_toc_provider = nil, nil, nil
end

function FloNovel:queueNativeToc()
    self:clearNativeToc()
    if self.options.chapters then UIManager:scheduleIn(0.2, self.prepare_toc) end
end

function FloNovel:buildNativeToc()
    local doc = self.ui.document
    if not self.options.chapters or not doc or not self.ui.rolling
        or not doc.file:lower():match("%.txt$") or self.ui.typeset.txt_preformatted ~= 1
        or (self.ui.handmade and self.ui.handmade.toc_enabled) then return end
    local generation, original = self.chapter_generation, rawget(doc, "getToc")
    local Trapper = require("ui/trapper")
    -- A default invisible trap cancels on the first page tap. Keep its explicit
    -- cancellation handle off the input stack so reading can continue instead.
    local control = {}
    self.chapter_task = control
    Trapper:wrap(function()
        local complete, entries, reason = Trapper:dismissableRunInSubprocess(function()
            local ok, result, failure = pcall(Chapters.build, doc, doc.file)
            if not ok then return nil, "chapter_preparation_failed" end
            return result, failure
        end, control)
        control.dismiss_callback = nil
        if self.chapter_task == control then self.chapter_task = nil end
        if generation ~= self.chapter_generation or self.ui.document ~= doc or not self.options.chapters
            or (self.ui.handmade and self.ui.handmade.toc_enabled) or rawget(doc, "getToc") ~= original then return end
        if not complete or not entries then
            self.chapter_error = reason or "chapter_preparation_cancelled"
            if complete then self:notice(self.chapter_error) end
            return
        end
        self.chapter_error = nil
        if #entries == 0 then return end
        self.original_toc_provider, self.toc_document = original, doc
        self.toc_provider = function() return require("util").tableDeepCopy(entries) end
        doc.getToc = self.toc_provider
        self.ui:handleEvent(Event:new("UpdateToc"))
        self.ui.toc:fillToc()
        self.ui.view.footer:maybeUpdateFooter()
        require("logger").info("FloNovel native ToC ready; entries=" .. #entries)
    end)
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
              self:queueNativeToc()
              self:saveOptions()
          end },
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
        local reason = self.sync_error or self.chapter_error
        self:message(reason and tr("unavailable", reason) or tr("status_ok"))
    end }
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
    self:queueNativeToc()
    if self.options.position then UIManager:scheduleIn(1, self.initial_fetch) end
end
function FloNovel:onCloseDocument()
    self:clearNativeToc()
    UIManager:unschedule(self.checkpoint)
    UIManager:unschedule(self.initial_fetch)
    self:checkpointPosition(false)
    self:invalidate()
    if self.options.position then UIManager:scheduleIn(0, self.close_flush) end
end
function FloNovel:onDocumentRerendered()
    self:invalidate()
    self:queueNativeToc()
end
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
