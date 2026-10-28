-- Preserve decoded UTF-16 offsets; reject engine transformations instead of guessing.
local Map = {}
Map.__index = Map

local function scan(text, visit)
    local byte, units, points = 1, 0, 0
    while byte <= #text do
        local lead = text:byte(byte)
        local size, code
        if lead < 0x80 then size, code = 1, lead
        elseif lead >= 0xC2 and lead <= 0xDF then size, code = 2, lead - 0xC0
        elseif lead >= 0xE0 and lead <= 0xEF then size, code = 3, lead - 0xE0
        elseif lead >= 0xF0 and lead <= 0xF4 then size, code = 4, lead - 0xF0
        else return nil, "invalid_utf8" end
        for index = 1, size - 1 do
            local tail = text:byte(byte + index)
            if not tail or tail < 0x80 or tail > 0xBF then return nil, "invalid_utf8" end
            code = code * 64 + tail - 0x80
        end
        if (size == 3 and code < 0x800) or (size == 4 and code < 0x10000)
            or (code >= 0xD800 and code <= 0xDFFF) or code > 0x10FFFF then
            return nil, "invalid_utf8"
        end
        if code == 0 then return nil, "unsupported_nul" end
        if visit then visit(byte, units, points, code, size) end
        units = units + (code > 0xFFFF and 2 or 1)
        points, byte = points + 1, byte + size
    end
    if visit then visit(byte, units, points) end
    return units
end

Map.utf16_length = scan

local function build_reference(doc, source, options)
    options = options or {}
    if source:sub(1, 3) == "\239\187\191" then source = source:sub(4) end
    local length, err = scan(source)
    if not length then return nil, err end
    local self = setmetatable({ nodes = {}, length = length, doc = doc }, Map)
    local cursor, source_units, node_index = 1, 0, 1
    while cursor <= #source do
        local delimiter = source:find("[\r\n]", cursor)
        local line = source:sub(cursor, delimiter and delimiter - 1 or #source)
        local line_units = assert(scan(line))
        if line:find("[^ \t]") then
            local consumed, consumed_units = 0, 0
            repeat
                local path = "/FictionBook/body/pre[" .. node_index .. "]/text()"
                local parsed = doc:getTextFromXPointer(path)
                if not parsed or #parsed == 0 then
                    return nil, "parser_changed_text_at_node_" .. node_index
                end
                local parsed_units = assert(scan(parsed))
                local advance_bytes, advance_units = #parsed, parsed_units
                local start, blocked = source_units + consumed_units, false
                if source:sub(cursor + consumed, cursor + consumed + #parsed - 1) ~= parsed
                    or consumed + #parsed > #line then
                    if not options.known_line_changes or consumed ~= 0 then
                        return nil, "parser_changed_text_at_node_" .. node_index
                    end
                    local prefix, interior = line:match("^([ \t]*\20)(.*)\21$")
                    local compact = line:gsub("[ \t]", "")
                    local separator = compact:sub(1, 1)
                    local is_separator = (separator == "-" or separator == "*" or separator == "=")
                        and compact:gsub(separator == "-" and "%-" or separator == "*" and "%*" or "=", "") == ""
                    if prefix and parsed == interior then
                        start = source_units + assert(scan(prefix))
                    elseif is_separator and parsed == "* * *" then
                        blocked = true
                    else
                        return nil, "unrecognized_parser_change_at_node_" .. node_index
                    end
                    advance_bytes, advance_units = #line, line_units
                end
                self.nodes[#self.nodes + 1] = {
                    path = path, text = parsed, start = start, blocked = blocked,
                    length = blocked and line_units or parsed_units, index = node_index,
                }
                consumed, consumed_units = consumed + advance_bytes, consumed_units + advance_units
                node_index = node_index + 1
            until consumed == #line
        end
        source_units = source_units + line_units
        if not delimiter then break end
        local first, second = source:sub(delimiter, delimiter), source:sub(delimiter + 1, delimiter + 1)
        local width = ((second == "\r" or second == "\n") and first ~= second) and 2 or 1
        source_units, cursor = source_units + width, delimiter + width
    end
    if doc:getTextFromXPointer("/FictionBook/body/pre[" .. node_index .. "]/text()") ~= nil then
        return nil, "unexpected_extra_dom_text"
    end
    if #self.nodes == 0 then return nil, "no_visible_text" end
    return self
end

function Map:to_xpointer(offset)
    if type(offset) ~= "number" or offset ~= math.floor(offset) or offset < 0 or offset > self.length then
        return nil, "offset_out_of_range"
    end
    for _, node in ipairs(self.nodes) do
        if offset >= node.start and offset < node.start + node.length then
            if node.blocked then return nil, "transformed_source_region" end
            local point
            scan(node.text, function(_, units, points)
                if node.start + units == offset then point = points end
            end)
            if not point then return nil, "inside_surrogate_pair" end
            return node.path .. "." .. point
        end
    end
    return nil, "unrepresented_boundary"
end

-- Hidden whitespace and surrogate interiors must never be rounded into another position.
function Map:to_exact_boundary(offset)
    if offset == self.length and self.eof then return self.eof end
    local xp, reason = self:to_xpointer(offset)
    if xp then return xp end
    if reason ~= "unrepresented_boundary" then return nil, reason end
    for _, node in ipairs(self.nodes) do
        if not node.blocked and offset == node.start + node.length then
            local points
            scan(node.text, function(_, _, count) points = count end)
            local ending = node.path .. "." .. points
            if self.doc:isXPointerInDocument(ending) and self:from_xpointer(ending) == offset then
                return ending
            end
        end
    end
    return nil, reason
end

function Map:from_xpointer(xp)
    if type(xp) ~= "string" then return nil, "invalid_xpointer" end
    local normalized = self.doc:getNormalizedXPointer(xp)
    if self.eof_normalized and normalized == self.eof_normalized then return self.length end
    if not normalized then return nil, "invalid_xpointer" end
    local index, point = normalized:match("^/FictionBook/body/pre%[(%d+)%]/text%(%)%.(%d+)$")
    -- Normalized paths omit [1] when only one matching sibling exists.
    if not index then
        point = normalized:match("^/FictionBook/body/pre/text%(%)%.(%d+)$")
        if point then index = 1 end
    end
    index, point = tonumber(index), tonumber(point)
    local node = index and self.nodes[index]
    if not node then return nil, "unmapped_xpointer" end
    if node.blocked then return nil, "transformed_source_region" end
    local offset
    scan(node.text, function(_, units, points)
        if points == point then offset = node.start + units end
    end)
    if offset then return offset end
    return nil, "xpointer_out_of_range"
end

local entities = {lt="<",gt=">",amp="&",quot='"',apos="'"}
local function decode(text)
    local invalid = false
    local decoded = text:gsub("&([^;]+);", function(entity)
        if entities[entity] then return entities[entity] end
        -- The inspected TXT exporter uses XML's five named entities only.
        invalid = true
        return "&" .. entity .. ";"
    end)
    if invalid then return nil, "unsupported_serialized_entity" end
    return decoded
end

local function extract(xml)
    if type(xml) ~= "string" or xml:sub(1,6) ~= "<body>" or xml:sub(-7) ~= "</body>" then
        return nil, "unsupported_serialized_body"
    end
    local nodes, cursor, limit = {}, 7, #xml - 7
    while cursor <= limit do
        if xml:sub(cursor,cursor+4) == "<pre>" then
            local ending = xml:find("</pre>",cursor+5,true)
            if not ending or ending > limit then return nil,"unclosed_serialized_pre" end
            local encoded = xml:sub(cursor+5,ending-1)
            if encoded:find("<",1,true) then return nil,"unsupported_nested_dom" end
            local text, reason = decode(encoded)
            if not text then return nil,reason end
            nodes[#nodes+1] = text
            cursor = ending+6
        elseif xml:sub(cursor,cursor+12) == "<empty-line/>" then
            cursor = cursor+13
        else
            return nil,"unsupported_serialized_element"
        end
    end
    if cursor ~= limit+1 then return nil,"invalid_serialized_extent" end
    return nodes
end


function Map.build(doc, source, options)
    local valid, invalid = scan(source)
    if not valid then return nil, invalid end
    local xml = doc:getHTMLFromXPointer("/FictionBook/body")
    local nodes, reason = extract(xml)
    if not nodes then return nil, reason end
    local facade = { getTextFromXPointer = function(_, path)
        local index = path:match("^/FictionBook/body/pre%[(%d+)%]/text%(%)$")
        return index and nodes[tonumber(index)] or nil
    end }
    local map, failure = build_reference(facade, source, options)
    if not map then return nil, failure end
    map.doc = doc
    local _, pre = xml:gsub("<pre>", "")
    local _, empty = xml:gsub("<empty%-line/>", "")
    local ending = "/FictionBook/body." .. (pre + empty)
    if not doc:isXPointerInDocument(ending) then return nil, "invalid_body_end" end
    map.eof, map.eof_normalized = ending, doc:getNormalizedXPointer(ending)
    if not map.eof_normalized then return nil, "invalid_body_end" end
    return map
end

return Map
