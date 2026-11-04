-- Supply ephemeral ## entries to KOReader's own table of contents.
local Hash = require("flonovel.content_hash")
local Map = require("flonovel.position_map")
local Chapters = {}

function Chapters.scan(path)
    local file = io.open(path, "rb")
    if not file then return nil, "source_unreadable" end
    local entries, buffer, pre, child, first = {}, "", 0, 0, true
    local function line(text)
        if first then
            if text:sub(1, 3) == "\239\187\191" then text = text:sub(4) end
            first = false
        end
        child = child + 1
        if text:find("[^ \t]") then
            pre = pre + 1
            if text:sub(1, 2) == "##" then
                entries[#entries + 1] = { title = text:gsub("[ \t]+$", ""), text = text,
                    pre = pre, child = child }
            end
        end
    end
    while true do
        local chunk = file:read(65536)
        if not chunk then break end
        buffer = buffer .. chunk
        local cursor = 1
        while true do
            local ending = buffer:find("[\r\n]", cursor)
            -- Keep a terminal delimiter until its possible paired byte arrives.
            if not ending or ending == #buffer then break end
            line(buffer:sub(cursor, ending - 1))
            local a, b = buffer:sub(ending, ending), buffer:sub(ending + 1, ending + 1)
            cursor = ending + (((b == "\r" or b == "\n") and a ~= b) and 2 or 1)
        end
        buffer = buffer:sub(cursor)
    end
    file:close()
    if buffer ~= "" then
        if buffer:find("[\r\n]$") then buffer = buffer:sub(1, -2) end
        line(buffer)
    end
    return entries
end

function Chapters.build(document, path)
    local before, reason = Hash.file(path)
    if not before then return nil, reason end
    local entries, failure = Chapters.scan(path)
    if not entries then return nil, failure end
    local toc = {}
    for _, entry in ipairs(entries) do
        -- Address the exact source child instead of a pre-only ordinal. This
        -- also accounts for intervening native empty-line elements.
        local xp = "/FictionBook/body/" .. entry.child .. "/1.0"
        -- Source ordinals only propose a node. A transformed TXT must never
        -- silently supply a different heading to native chapter navigation.
        if not Map.utf16_length(entry.text) or document:getTextFromXPointer(xp:sub(1, -3)) ~= entry.text then
            return nil, "chapter_native_layout_mismatch"
        end
        local page = document:getPageFromXPointer(xp)
        if not page or page < 1 then return nil, "chapter_native_page_missing" end
        toc[#toc + 1] = { title = entry.title, xpointer = xp, page = page, depth = 1 }
    end
    if Hash.file(path) ~= before then return nil, "source_changed_during_chapters" end
    return toc
end

return Chapters
