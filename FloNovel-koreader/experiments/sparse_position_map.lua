-- Experimental, validated sparse TXT index. Never used by the product plugin.
local Map = require("flonovel.position_map")
local Hash = require("flonovel.content_hash")
local json = require("json")
local Sparse = {}
Sparse.__index = Sparse
local STRIDE, MAX_LINE = 4096, 1024
local WINDOW = STRIDE + MAX_LINE * 2 + 4

local function line_at(text, cursor)
    local delimiter = text:find("[\r\n]", cursor)
    local ending = delimiter and delimiter - 1 or #text
    local following = #text + 1
    if delimiter then
        local a, b = text:sub(delimiter, delimiter), text:sub(delimiter + 1, delimiter + 1)
        following = delimiter + (((b == "\r" or b == "\n") and a ~= b) and 2 or 1)
    end
    return text:sub(cursor, ending), following, delimiter and following - delimiter or 0
end

function Sparse.prepare(doc, path)
    local file = assert(io.open(path, "rb"))
    local source = file:read("*a"); file:close()
    -- This first experiment pays full validation once. Sparse source-only
    -- inference is not accepted: repeated paragraphs could hide misalignment.
    local map, reason = Map.build(doc, source, { known_line_changes = true })
    if not map then return nil, reason end
    local cursor = source:sub(1, 3) == "\239\187\191" and 4 or 1
    local units, pre, empty, checkpoints = 0, 0, 0, {{ cursor, 0, 0, 0 }}
    while cursor <= #source do
        local line, following, delimiter = line_at(source, cursor)
        if #line > MAX_LINE then return nil, "prototype_long_line" end
        local length
        if line:find("[^ \t]") then
            pre = pre + 1
            local node = map.nodes[pre]
            if not node or node.blocked or node.text ~= line or node.start ~= units then
                return nil, "prototype_transformed_or_split_line"
            end
            length = node.length
        else
            empty = empty + 1
            if not map.empty_starts or map.empty_starts[empty] ~= units then return nil, "prototype_layout_mismatch" end
            length = #line
        end
        units = units + length + delimiter
        cursor = following
        if cursor <= #source and cursor - checkpoints[#checkpoints][1] >= STRIDE then
            checkpoints[#checkpoints + 1] = { cursor, units, pre, empty }
        end
    end
    if units ~= map.length or pre ~= #map.nodes then return nil, "prototype_extent_mismatch" end
    local payload = { 1, Hash.text(source), #source, units, pre, empty,
        map.eof, map.eof_normalized, doc:getDocumentRenderingHash(true), checkpoints }
    return { payload = payload, digest = Hash.text(json.encode(payload)) }, nil, map
end

function Sparse.open(doc, path, saved)
    if type(saved) ~= "table" or type(saved.payload) ~= "table" then return nil, "invalid_index" end
    local p = saved.payload
    if saved.digest ~= Hash.text(json.encode(p)) or p[1] ~= 1 then return nil, "invalid_index" end
    if Hash.file(path) ~= p[2] then return nil, "source_changed" end
    if doc:getDocumentRenderingHash(true) ~= p[9] then return nil, "rendering_changed" end
    local file = io.open(path, "rb")
    if not file then return nil, "source_unreadable" end
    return setmetatable({ doc = doc, file = file, p = p, bytes_read = 0 }, Sparse)
end

function Sparse:close()
    if self.file then self.file:close(); self.file = nil end
end

function Sparse:checkpoint(field, value, strict)
    local points, low, high, found = self.p[10], 1, #self.p[10], 1
    while low <= high do
        local middle = math.floor((low + high) / 2)
        local candidate = points[middle][field]
        if candidate < value or (not strict and candidate == value) then
            found, low = middle, middle + 1
        else high = middle - 1 end
    end
    return points[found]
end

function Sparse:window(checkpoint)
    if not self.file then return nil, "closed_index" end
    if self.doc:getDocumentRenderingHash(true) ~= self.p[9] then return nil, "rendering_changed" end
    if not self.file:seek("set", checkpoint[1] - 1) then return nil, "seek_failed" end
    local value = self.file:read(WINDOW) or ""
    self.bytes_read = self.bytes_read + #value
    return value
end

function Sparse:from_xpointer(xp)
    if type(xp) ~= "string" then return nil, "invalid_xpointer" end
    -- The complete source/DOM relation was verified before saving this index,
    -- and open() binds it to the source hash and rendering. Canonical TXT
    -- paths can therefore be decoded without walking thousands of siblings
    -- again for normalization and text extraction on every position update.
    local canonical = xp == self.p[7] or xp == self.p[8]
        or xp:match("^/FictionBook/body/pre%[%d+%]/text%(%)%.%d+$")
        or xp:match("^/FictionBook/body/pre/text%(%)%.%d+$")
        or xp:match("^/FictionBook/body/empty%-line%[%d+%]%.0$")
        or xp == "/FictionBook/body/empty-line.0"
    local normalized = canonical and xp or self.doc:getNormalizedXPointer(xp)
    if not normalized then return nil, "invalid_xpointer" end
    if self.doc:getDocumentRenderingHash(true) ~= self.p[9] then return nil, "rendering_changed" end
    if normalized == self.p[8] then return self.p[4] end
    local pre, point = normalized:match("^/FictionBook/body/pre%[(%d+)%]/text%(%)%.(%d+)$")
    if not pre then
        point = normalized:match("^/FictionBook/body/pre/text%(%)%.(%d+)$")
        if point then pre = 1 end
    end
    local blank = normalized:match("^/FictionBook/body/empty%-line%[(%d+)%]%.0$")
    if normalized == "/FictionBook/body/empty-line.0" then blank = 1 end
    pre, point, blank = tonumber(pre), tonumber(point), tonumber(blank)
    if not pre and not blank then return nil, "unsupported_xpointer" end
    if blank and blank == self.p[6] + 1 and self.p[7]:match("%.(%d+)$") == tostring(self.p[5] + self.p[6] + 1) then
        return self.p[4]
    end
    local cp = self:checkpoint(pre and 3 or 4, pre or blank, true)
    local text, reason = self:window(cp)
    if not text then return nil, reason end
    local cursor, units, pres, empties = 1, cp[2], cp[3], cp[4]
    while cursor <= #text do
        local line, following, delimiter = line_at(text, cursor)
        local visible = line:find("[^ \t]") ~= nil
        if visible then pres = pres + 1 else empties = empties + 1 end
        if (pre and visible and pres == pre) or (blank and not visible and empties == blank) then
            if blank then return units end
            local offset
            local length = Map.utf16_length(line, function(_, count, points)
                if points == point then offset = units + count end
            end)
            if not length or offset == nil then return nil, "invalid_local_boundary" end
            return offset
        end
        local length, failure = Map.utf16_length(line)
        if not length then return nil, failure end
        units, cursor = units + length + delimiter, following
    end
    return nil, "window_exhausted"
end

function Sparse:to_xpointer(offset)
    if type(offset) ~= "number" or offset ~= math.floor(offset) or offset < 0 or offset > self.p[4] then
        return nil, "offset_out_of_range"
    end
    if self.doc:getDocumentRenderingHash(true) ~= self.p[9] then return nil, "rendering_changed" end
    if offset == self.p[4] then return self.p[7] end
    local cp = self:checkpoint(2, offset, false)
    local text, reason = self:window(cp)
    if not text then return nil, reason end
    local cursor, units, pre, empty = 1, cp[2], cp[3], cp[4]
    while cursor <= #text do
        local line, following, delimiter = line_at(text, cursor)
        local visible = line:find("[^ \t]") ~= nil
        if visible then pre = pre + 1 else empty = empty + 1 end
        local point
        local length, failure = Map.utf16_length(line, function(_, count, points)
            if units + count == offset then point = points end
        end)
        if not length then return nil, failure end
        if offset <= units + length then
            local xp
            if visible and point then xp = "/FictionBook/body/pre[" .. pre .. "]/text()." .. point
            elseif not visible and offset == units then xp = "/FictionBook/body/empty-line[" .. empty .. "].0" end
            if xp and self:from_xpointer(xp) == offset then return xp end
            return nil, "unrepresented_boundary"
        end
        if offset < units + length + delimiter then return nil, "unrepresented_boundary" end
        units, cursor = units + length + delimiter, following
    end
    return nil, "window_exhausted"
end

return Sparse
