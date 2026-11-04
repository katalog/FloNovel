local lfs = require("libs/libkoreader-lfs")
local util = require("ffi/util")
local utf8 = require("ffi/utf8proc")
local Map = require("flonovel.position_map")
local cases = require("flonovel.case_table")
local Paths = {}

local function assigned(cp)
    local left, right = 1, #cases.assigned
    while left <= right do
        local mid = math.floor((left + right) / 2)
        local range = cases.assigned[mid]
        if cp < range[1] then right = mid - 1
        elseif cp > range[2] then left = mid + 1
        else return true end
    end
    return false
end

function Paths.relative(path)
    if type(path) ~= "string" or path == "" or path:find("%z") or path:find("[\1-\31\127]") then
        return nil, "invalid_path"
    end
    if not Map.utf16_length(path) then return nil, "invalid_path_utf8" end
    path = path:gsub("\\", "/")
    if path:sub(1, 1) == "/" or path:find(":", 1, true) or path:sub(-1) == "/" or path:find("//", 1, true) then
        return nil, "absolute_or_empty_path"
    end
    for part in path:gmatch("[^/]+") do
        if part == "." or part == ".." or part:lower() == ".flonovel" or part:sub(-1):match("[ .]") then
            return nil, "unsafe_path_component"
        end
    end
    return path
end

function Paths.key(path)
    local normalized, reason = Paths.relative(path)
    if not normalized then return nil, reason end
    local unsupported
    Map.utf16_length(normalized, function(_, _, _, cp)
        if cp and (cp == 0x3A3 or not assigned(cp)) then unsupported = true end
    end)
    -- ROOT lowercase has contextual final-sigma rules. Defer such keys until a
    -- complete JVM word-boundary implementation is verified; never use casefold.
    if unsupported then return nil, "unsupported_unicode_key" end
    normalized = utf8.normalize_NFC(normalized)
    local parts = {}
    Map.utf16_length(normalized, function(byte, _, _, cp, size)
        if cp then parts[#parts + 1] = cases.lower[cp] or normalized:sub(byte, byte + size - 1) end
    end)
    return table.concat(parts)
end

function Paths.root(path)
    if type(path) ~= "string" or path:find("%z") or lfs.symlinkattributes(path, "mode") ~= "directory" then
        return nil, "library_missing_or_linked"
    end
    local real = util.realpath(path)
    if not real or real == "/" then return nil, "invalid_library_root" end
    return (real:gsub("/+$", ""))
end

function Paths.resolve(root, relative, create_parents)
    local path, reason = Paths.relative(relative)
    if not path then return nil, reason end
    if Paths.root(root) ~= root then return nil, "library_changed" end
    local current = root
    local parts = {}
    for part in path:gmatch("[^/]+") do parts[#parts + 1] = part end
    for index, part in ipairs(parts) do
        current = current .. "/" .. part
        local mode = lfs.symlinkattributes(current, "mode")
        if mode == "link" then return nil, "linked_path" end
        if index < #parts then
            if not mode and create_parents == true then
                local ok = lfs.mkdir(current)
                if not ok then return nil, "parent_creation_failed" end
                mode = lfs.symlinkattributes(current, "mode")
            end
            if mode ~= "directory" and not (mode == nil and create_parents == "plan") then
                return nil, "parent_missing"
            end
        elseif mode and mode ~= "file" then return nil, "target_not_regular_file" end
    end
    return current
end

return Paths
