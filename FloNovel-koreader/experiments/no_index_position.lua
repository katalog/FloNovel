-- Linux emulator experiment only. No installation or dependency in the Kindle product.
local ffi = require("ffi")
local Map = require("flonovel.position_map")
local Hash = require("flonovel.content_hash")
local Position = require("flonovel.reading_position")
local UIManager = require("ui/uimanager")
local Event = require("ui/event")
local tr = require("flonovel.strings")
local clock = require("socket").gettime
ffi.cdef[[void *iconv_open(const char *,const char *);
size_t iconv(void *,char **,size_t *,char **,size_t *);int iconv_close(void *);]]
local Probe = {}

local function convert(text, capacity)
    local handle = ffi.C.iconv_open("UTF-16LE", "UTF-8")
    if handle == ffi.cast("void *", -1) then return nil, "experiment_iconv_unavailable" end
    local buffer = ffi.new("char[?]", math.max(capacity, 1))
    local input, output = ffi.new("char *[1]", ffi.cast("char *", text)), ffi.new("char *[1]", buffer)
    local left, free = ffi.new("size_t[1]", #text), ffi.new("size_t[1]", capacity)
    local result = ffi.C.iconv(handle, input, left, output, free)
    ffi.C.iconv_close(handle)
    return { units = (capacity - tonumber(free[0])) / 2, bytes = #text - tonumber(left[0]),
        complete = result ~= ffi.cast("size_t", -1) and left[0] == 0 }
end

local function source(path)
    local file = io.open(path, "rb")
    if not file then return nil, "source_unreadable" end
    local text = file:read(16 * 1024 * 1024 + 1) or ""; file:close()
    if #text > 16 * 1024 * 1024 then return nil, "experiment_file_too_large" end
    return text
end

function Probe.capture(ui)
    if not ui.document or not ui.rolling or ui.typeset.txt_preformatted ~= 1 then return nil, "experiment_not_preformatted_txt" end
    local xp = ui.rolling:getLastProgress()
    local normalized = ui.document:getNormalizedXPointer(xp)
    local path, point
    if normalized then path, point = normalized:match("^(.-/text%(%))%.(%d+)$") end
    if not path then return nil, "experiment_non_text_anchor_held" end
    local text = ui.document:getTextFromXPointer(path)
    if not text or text == "" or #text > 1024 then return nil, "experiment_context_unsupported" end
    local local_units
    local valid = Map.utf16_length(text, function(_, units, points)
        if points == tonumber(point) then local_units = units end
    end)
    if not valid or local_units == nil then return nil, "experiment_invalid_local_point" end
    return { xp = xp, text = text, local_units = local_units }
end

function Probe.locate(path, captured)
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
            if found then return nil, "experiment_ambiguous_context_held" end
            found = first
        end
        cursor = last + 1
    end
    if not found then return nil, "experiment_context_not_found" end
    local prefix = decoded:sub(1, found - 1)
    local converted, failure = convert(prefix, #prefix * 2 + 2)
    if not converted or not converted.complete then return nil, failure or "experiment_invalid_utf8" end
    return { offset = converted.units + captured.local_units, hash = Hash.text(text) }
end

function Probe.reverse(path, offset)
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
        if #line > 1024 or line:find("[%z\1-\8\11\12\14-\31]") then return nil, "experiment_layout_unsupported" end
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
    return nil, "experiment_eof_held"
end

function Probe.install(FloNovel)
    local function allowed(self, config)
        local home = assert(os.getenv("KO_HOME"))
        return config and config.account_id == "synthetic-emulator" and config.library_root == home .. "/library"
            and self.ui.document and self.ui.document.file:sub(1, #config.library_root + 1) == config.library_root .. "/"
    end
    function FloNovel:checkpointPosition(send, interactive)
        if not self.options.position then return end
        if not self.ui.document then if send then self:flushPending(interactive) end; return end
        local config, directory = self:loadConnection()
        if not allowed(self, config) then return end
        local identity = self:bookIdentity(config)
        local captured, failure = Probe.capture(self.ui)
        if not identity or not captured then
            self.sync_error = failure or "experiment_identity_missing"
            if interactive then self:notice(self.sync_error) end
            return
        end
        local function task()
            local started = clock()
            local value, reason = Probe.locate(identity.physical, captured)
            if not value then return nil, reason end
            local client = Position.new(config)
            local ok, error = client:enqueue(directory .. "/positions.json", identity.relative, value.offset, value.hash)
            if not ok then return nil, error end
            local report = { sent = 0, deferred = {} }
            if send then report, error = client:flush(directory .. "/positions.json") end
            if not report then return nil, error end
            require("logger").info(string.format("FloNovel no-index checkpoint wall=%.3fs offset=%d", clock() - started, value.offset))
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
        if not self.options.position then return end
        local config = self:loadConnection()
        if not allowed(self, config) then return end
        local identity = self:bookIdentity(config)
        if not identity then return end
        self:runNetwork("position", function() return Position.new(config):fetch(identity.key) end,
            function(remote, generation)
                if remote.absent or remote.char_offset == 0 then return end
                local captured, failure = Probe.capture(self.ui)
                if not captured then self.sync_error = failure; return end
                self:runNetwork("position", function()
                    local current, reason = Probe.locate(identity.physical, captured)
                    if not current then return nil, reason end
                    if remote.char_offset - current.offset <= 500 then return { absent = true } end
                    return Probe.reverse(identity.physical, remote.char_offset)
                end, function(target)
                    if target.absent or generation ~= self.generation or not self:connectionMatches(config)
                        or self.ui.rolling:getLastProgress() ~= captured.xp then return end
                    if target.node_path and self.ui.document:getTextFromXPointer(target.node_path) ~= target.text then return end
                    if not self.ui.document:isXPointerInDocument(target.xp) then return end
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

return Probe
