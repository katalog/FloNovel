-- Default ## marker and subdivision diagnostics; no user-regex support or persistence.
local Map = dofile("../../../../experiments/txt_position_map.lua")
local Probe = {}

local function is_space(code)
    -- Kotlin trim includes Unicode space separators, including NBSP; Lua %s does not.
    return (code >= 9 and code <= 13) or (code >= 28 and code <= 32)
        or code == 0xA0 or code == 0x1680 or (code >= 0x2000 and code <= 0x200A)
        or code == 0x2028 or code == 0x2029 or code == 0x202F or code == 0x205F or code == 0x3000
end

local function trim(line)
    local first, last
    assert(Map.utf16_length(line, function(byte, _, _, code, size)
        if code and not is_space(code) then first = first or byte; last = byte + size - 1 end
    end))
    return first and line:sub(first, last) or ""
end

function Probe.breakpoints(text, divisions)
    local chapters, units, cursor = {}, 0, 1
    while cursor <= #text do
        local ending = text:find("\n", cursor, true)
        local line = text:sub(cursor, ending and ending - 1 or #text)
        local title = trim(line)
        if title:sub(1, 2) == "##" then
            chapters[#chapters + 1] = { title = title, offset = units, byte = cursor }
        end
        units = units + assert(Map.utf16_length(line)) + (ending and 1 or 0)
        if not ending then break end
        cursor = ending + 1
    end
    local points, seen = {}, {}
    for index, chapter in ipairs(chapters) do
        local following = chapters[index + 1]
        local ending = following and following.offset or units
        local segment = text:sub(chapter.byte, following and following.byte - 1 or #text)
        local _, line_count = segment:gsub("\n", "")
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

return Probe
