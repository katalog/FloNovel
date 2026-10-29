-- Derive trusted ## chapter boundaries from the source on every open.
local Map = require("flonovel.position_map")
local Probe = {}

local function is_space(code)
    -- Kotlin trim includes Unicode space separators, including NBSP; Lua %s does not.
    return (code >= 9 and code <= 13) or (code >= 28 and code <= 32)
        or code == 0xA0 or code == 0x1680 or (code >= 0x2000 and code <= 0x200A)
        or code == 0x2028 or code == 0x2029 or code == 0x202F or code == 0x205F or code == 0x3000
end

function Probe.breakpoints(text, divisions)
    if type(divisions) ~= "number" or divisions < 1 or divisions > 32 or divisions ~= math.floor(divisions) then
        return nil, "invalid_divisions"
    end
    if text:sub(1, 3) == "\239\187\191" then text = text:sub(4) end
    local chapters, cursor, line_units, first, last, lf_count = {}, 1, 0, nil, nil, 0
    local chapter_lines = {}
    local function finish_line()
        if first and text:sub(first, first + 1) == "##" then
            chapters[#chapters + 1] = { title = text:sub(first, last), offset = line_units, byte = cursor }
            chapter_lines[#chapters] = lf_count
        end
        first, last = nil, nil
    end
    -- Validate, trim, count UTF-16 and count LF in one pass. Previously every
    -- ordinary paragraph was scanned repeatedly and allocated a trimmed copy.
    local units, reason = Map.utf16_length(text, function(byte, offset, _, code, size)
        if not code then finish_line()
        elseif code == 10 then
            finish_line()
            lf_count, cursor, line_units = lf_count + 1, byte + 1, offset + 1
        elseif not is_space(code) then
            first, last = first or byte, byte + size - 1
        end
    end)
    if not units then return nil, reason end
    local points, seen = {}, {}
    for index, chapter in ipairs(chapters) do
        local following = chapters[index + 1]
        local ending = following and following.offset or units
        local line_count = (following and chapter_lines[index + 1] or lf_count) - chapter_lines[index]
        if ending > chapter.offset then
            if following and line_count <= 20 then
                if not seen[ending] then points[#points + 1], seen[ending] = ending, true end
            else
                for part = 1, divisions do
                    local point = chapter.offset + math.floor((ending - chapter.offset) * part / divisions)
                    if not seen[point] then points[#points + 1], seen[point] = point, true end
                end
            end
        end
    end
    table.sort(points)
    return points, chapters
end

function Probe.target(points, current, forward)
    if forward then
        for _, point in ipairs(points) do if point > current then return point end end
    else
        for index = #points, 1, -1 do if points[index] < current then return points[index] end end
    end
    return nil, "no_target_in_direction"
end

return Probe
