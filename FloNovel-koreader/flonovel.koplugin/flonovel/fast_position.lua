-- Opt-in source-text position lookup; never build a whole-book DOM map on this path.
local ffi = require("ffi")
local Map = require("flonovel.position_map")
local Hash = require("flonovel.content_hash")
local Position = require("flonovel.reading_position")
local tr = require("flonovel.strings")
local clock = require("socket").gettime
require("ffi/utf8proc_h")
ffi.cdef[[utf8proc_ssize_t utf8proc_decompose(const utf8proc_uint8_t *,utf8proc_ssize_t,
    utf8proc_int32_t *,utf8proc_ssize_t,int);char *strpbrk(const char *,const char *);]]
local loaded, unicode = pcall(ffi.loadlib, "utf8proc", "3")
local Fast = {}
local controls = string.char(1,2,3,4,5,6,7,8,11,12,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31)
local function units(text)
    if not loaded or text:find("\0", 1, true) then return nil end
    local count = tonumber(unicode.utf8proc_decompose(text, #text, nil, 0, 0))
    if count < 0 then return nil end
    local cursor = ffi.cast("const char *", text)
    while true do
        local found = ffi.C.strpbrk(cursor, "\240\241\242\243\244")
        if found == nil then break end
        count, cursor = count + 1, found + 1
    end
    return count
end
local supported = pcall(function()
    assert(units("abc") == 3 and units("한글") == 2 and units("a😀") == 3)
end)
Fast.supported = supported
local function convert(text, capacity)
    if not supported then return nil, "fast_native_unavailable" end
    local length = units(text)
    if not length then return nil, "invalid_utf8" end
    local target = capacity / 2
    if length <= target then return {units=length,bytes=#text,complete=true} end
    local low, high, best, best_units = 0, math.min(#text, target * 3), 0, 0
    while low <= high do
        local middle = math.floor((low + high) / 2)
        local boundary = middle
        while boundary > 0 do
            local byte = text:byte(boundary + 1)
            if not byte or byte < 128 or byte > 191 then break end
            boundary = boundary - 1
        end
        local count = units(text:sub(1, boundary))
        if not count then return nil, "invalid_utf8" end
        if count <= target then
            best, best_units, low = boundary, count, middle + 1
        else high = middle - 1 end
    end
    return {units=best_units,bytes=best,complete=false}
end

local function source(path)
    local file = io.open(path, "rb")
    if not file then return nil, "source_unreadable" end
    local text = file:read(16 * 1024 * 1024 + 1) or ""; file:close()
    if #text > 16 * 1024 * 1024 then return nil, "fast_file_too_large" end
    if not supported then return nil, "fast_native_unavailable" end
    if text:find("\0", 1, true) or tonumber(unicode.utf8proc_decompose(text, #text, nil, 0, 0)) < 0 then
        return nil, "invalid_utf8"
    end
    if ffi.C.strpbrk(text, controls) ~= nil then return nil, "fast_layout_unsupported" end
    return text
end

function Fast.capture(ui)
    if not ui.document or not ui.rolling or ui.typeset.txt_preformatted ~= 1 then return nil, "fast_not_preformatted_txt" end
    local xp = ui.rolling:getLastProgress()
    local canonical = xp and (xp:match("^/FictionBook/body/pre%[%d+%]/text%(%)%.%d+$")
        or xp:match("^/FictionBook/body/pre/text%(%)%.%d+$"))
    local normalized = canonical and xp or ui.document:getNormalizedXPointer(xp)
    local path, point
    if normalized then path, point = normalized:match("^(.-/text%(%))%.(%d+)$") end
    if not path then return nil, "fast_non_text_anchor_held" end
    local ordinal = path:match("^/FictionBook/body/pre%[(%d+)%]/text%(%)$")
    if path == "/FictionBook/body/pre/text()" then ordinal = 1 end
    if not ordinal then return nil, "fast_layout_unsupported" end
    local text = ui.document:getTextFromXPointer(path)
    if not text or text == "" or #text > 1024 then return nil, "fast_context_unsupported" end
    local local_units
    local valid = Map.utf16_length(text, function(_, units, points)
        if points == tonumber(point) then local_units = units end
    end)
    if not valid or local_units == nil then return nil, "fast_invalid_local_point" end
    return { xp = xp, text = text, local_units = local_units, ordinal = tonumber(ordinal) }
end

function Fast.locate(path, captured)
    local text, reason = source(path)
    if not text then return nil, reason end
    local decoded = text:sub(1, 3) == "\239\187\191" and text:sub(4) or text
    local cursor, found = 1, nil
    while true do
        local first, last = decoded:find(captured.text, cursor, true)
        if not first then break end
        local before, after = decoded:sub(first - 1, first - 1), decoded:sub(last + 1, last + 1)
        if (first == 1 or before == "\r" or before == "\n")
            and (last == #decoded or after == "\r" or after == "\n") then
            if found then return nil, "fast_ambiguous_context_held" end
            found = first
        end
        cursor = last + 1
    end
    if not found then return nil, "fast_context_not_found" end
    local prefix = decoded:sub(1, found - 1)
    local ordinal = 1
    for line in prefix:gmatch("[^\r\n]+") do
        if #line > 1024 then return nil, "fast_layout_unsupported" end
        if line:find("[^ \t]") then ordinal = ordinal + 1 end
    end
    if ordinal ~= captured.ordinal then return nil, "fast_context_ordinal_mismatch" end
    local converted, failure = convert(prefix, #prefix * 2 + 2)
    if not converted or not converted.complete then return nil, failure or "fast_invalid_utf8" end
    return { offset = converted.units + captured.local_units, hash = Hash.text(text) }
end

function Fast.reverse(path, offset)
    if type(offset) ~= "number" or offset < 0 or offset ~= math.floor(offset) then return nil, "invalid_char_offset" end
    local text, reason = source(path)
    if not text then return nil, reason end
    local decoded = text:sub(1, 3) == "\239\187\191" and text:sub(4) or text
    if offset > #decoded then return nil, "offset_out_of_range" end
    local converted = convert(decoded, offset * 2)
    if not converted or converted.units ~= offset then return nil, "unrepresented_boundary" end
    local target, cursor, pre, empty = converted.bytes + 1, 1, 0, 0
    while cursor <= #decoded do
        local delimiter = decoded:find("[\r\n]", cursor)
        local ending = delimiter and delimiter - 1 or #decoded
        local line = decoded:sub(cursor, ending)
        if #line > 1024 then return nil, "fast_layout_unsupported" end
        local visible = line:find("[^ \t]") ~= nil
        if visible then pre = pre + 1 else empty = empty + 1 end
        if target <= ending + 1 then
            local xp, node_path
            if visible then
                local prefix = decoded:sub(cursor, target - 1)
                local _, tails = prefix:gsub("[\128-\191]", "")
                node_path = "/FictionBook/body/pre[" .. pre .. "]/text()"
                xp = node_path .. "." .. (#prefix - tails)
            elseif target == cursor then xp = "/FictionBook/body/empty-line[" .. empty .. "].0" end
            if not xp then return nil, "unrepresented_boundary" end
            return { xp = xp, node_path = node_path, text = line, hash = Hash.text(text) }
        end
        if not delimiter then break end
        local a, b = decoded:sub(delimiter, delimiter), decoded:sub(delimiter + 1, delimiter + 1)
        cursor = delimiter + (((b == "\r" or b == "\n") and a ~= b) and 2 or 1)
        if target < cursor then return nil, "unrepresented_boundary" end
    end
    return nil, "fast_eof_held"
end

function Fast.install(FloNovel)
    local UIManager, Event = require("ui/uimanager"), require("ui/event")
    local function snapshot(self)
        local xp = self.ui.rolling and self.ui.rolling:getLastProgress()
        return { document = self.ui.document, typeset = self.ui.typeset,
            rolling = { getLastProgress = function() return xp end } }, xp
    end
    local previous_checkpoint, previous_fetch = FloNovel.checkpointPosition, FloNovel.fetchPosition
    local previous_invalidate = FloNovel.invalidate
    function FloNovel:invalidate()
        self.fast_source_hash = nil
        return previous_invalidate(self)
    end
    local function allowed(self, config)
        return config and config.fast_position_enabled == true and self.ui.document
            and type(config.library_root) == "string"
            and self.ui.document.file:sub(1, #config.library_root + 1) == config.library_root .. "/"
    end
    function FloNovel:checkpointPosition(send, interactive)
        local selected = self:loadConnection()
        if not selected or not selected.fast_position_enabled then return previous_checkpoint(self, send, interactive) end
        if not self.options.position then return end
        if not self.ui.document then if send then self:flushPending(interactive) end; return end
        local config, directory = self:loadConnection()
        if not allowed(self, config) then return end
        if not supported then self.sync_error = "fast_native_unavailable"; if interactive then self:notice(self.sync_error) end; return end
        local identity = self:bookIdentity(config)
        if not self.fast_source_hash then
            if send then self:fetchPosition() end
            self.sync_error = "fast_source_binding_pending"
            if interactive then self:notice(self.sync_error) end
            return
        end
        local expected_hash = self.fast_source_hash
        local reader = snapshot(self)
        if not identity then
            self.sync_error = "fast_identity_missing"
            if interactive then self:notice(self.sync_error) end
            return
        end
        local function task()
            local started = clock()
            -- The fork owns a read-only snapshot of the document. Even a deep
            -- native XPath lookup must not run in the parent's input loop.
            local captured, failure = Fast.capture(reader)
            if not captured then return nil, failure end
            local value, reason = Fast.locate(identity.physical, captured)
            if not value then return nil, reason end
            if value.hash ~= expected_hash then return nil, "position_body_changed" end
            local client = Position.new(config)
            local ok, error = client:enqueue(directory .. "/positions.json", identity.relative, value.offset, value.hash)
            if not ok then return nil, error end
            local report = { sent = 0, deferred = {} }
            if send then report, error = client:flush(directory .. "/positions.json") end
            if not report then return nil, error end
            require("logger").info(string.format("FloNovel fast position checkpoint wall=%.3fs offset=%d", clock() - started, value.offset))
            return report
        end
        if send then
            self:runNetwork("position", task, function(report)
                if interactive then self:message(tr("position_done", report.sent, #report.deferred)) end
            end, interactive)
        else
            local ok, reason = task()
            if not ok then self.sync_error = reason end
        end
    end
    function FloNovel:fetchPosition()
        local selected = self:loadConnection()
        if not selected or not selected.fast_position_enabled then return previous_fetch(self) end
        if not self.options.position then return end
        local config = self:loadConnection()
        if not allowed(self, config) then return end
        local identity = self:bookIdentity(config)
        if not identity then return end
        if not supported then self.sync_error = "fast_native_unavailable"; return end
        self:runNetwork("position", function()
            local hash, failure = Hash.file(identity.physical)
            if not hash then return nil, failure end
            local remote, reason = Position.new(config):fetch(identity.key)
            if not remote then return nil, reason end
            return { remote = remote, hash = hash }
        end,
            function(response, generation)
                self.fast_source_hash = response.hash
                local remote = response.remote
                if remote.absent or remote.char_offset == 0 then return end
                local reader, original_xp = snapshot(self)
                self:runNetwork("position", function()
                    local captured, failure = Fast.capture(reader)
                    if not captured then return nil, failure end
                    local current, reason = Fast.locate(identity.physical, captured)
                    if not current then return nil, reason end
                    if current.hash ~= response.hash then return nil, "position_body_changed" end
                    if remote.char_offset - current.offset <= 500 then return { absent = true } end
                    local target, error = Fast.reverse(identity.physical, remote.char_offset)
                    if not target then return nil, error end
                    if target.node_path and reader.document:getTextFromXPointer(target.node_path) ~= target.text then
                        return nil, "fast_native_target_mismatch"
                    end
                    if not reader.document:isXPointerInDocument(target.xp) then return nil, "fast_invalid_target" end
                    return target
                end, function(target)
                    if target.absent or generation ~= self.generation or not self:connectionMatches(config)
                        or self.ui.rolling:getLastProgress() ~= original_xp then return end
                    UIManager:show(require("ui/widget/confirmbox"):new{
                        text = tr("remote_position", remote.char_offset),
                        ok_callback = function()
                            if generation == self.generation and self.options.position and self:connectionMatches(config)
                                and Hash.file(identity.physical) == target.hash then
                                self.ui:handleEvent(Event:new("GotoXPointer", target.xp))
                            end
                        end,
                    })
                end, false)
            end, false)
    end
end

return Fast
