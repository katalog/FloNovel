require("setupkoenv")
local project, home = assert(os.getenv("FLONOVEL_PROJECT")), assert(os.getenv("KO_HOME"))
package.path = project .. "/flonovel.koplugin/?.lua;" .. package.path
local json, sha = require("json"), require("ffi/sha2")
local Map, Chapters = require("flonovel.position_map"), require("flonovel.chapters")
local Paths, Hash = require("flonovel.paths"), require("flonovel.content_hash")
local function raw(name)
    local file = assert(io.open(project .. "/tests/fixtures/" .. name, "rb"))
    local text = file:read("*a"); file:close(); return text
end
local total, failed = 0, 0
local function check(name, value)
    total = total + 1
    if not value then failed = failed + 1; print("FAIL " .. name) end
end
local marker_raw = raw("marker-cases.json")
local cases, oracle = json.decode(marker_raw).cases, json.decode(raw("marker-oracle.json"))
check("marker oracle bound to inputs", sha.sha256(marker_raw) == oracle.manifest_sha256)
for index, case in ipairs(cases) do
    local expected = oracle.cases[index]
    check(case.name .. "/utf16", Map.utf16_length(case.text) == expected.length)
    for _, divisions in ipairs{ 1, 2, 4, 8, 16 } do
        local points, chapters = Chapters.breakpoints(case.text, divisions)
        local same = #chapters == #expected.chapters
        for i, chapter in ipairs(chapters) do
            same = same and expected.chapters[i] and chapter.title == expected.chapters[i].title and chapter.offset == expected.chapters[i].offset
        end
        check(case.name .. "/chapters/" .. divisions, same)
        local wanted = expected.points[tostring(divisions)]
        same = #points == #wanted
        for i, point in ipairs(points) do same = same and point == wanted[i] end
        check(case.name .. "/subdivisions/" .. divisions, same)
    end
end
local contract = json.decode(raw("contracts-oracle.json"))
check("contract oracle bound to inputs", sha.sha256(raw("contracts-input.json")) == contract.manifest_sha256)
for _, row in ipairs(contract.paths) do
    local key, reason = Paths.key(row.input)
    check("JVM key or explicit unsupported: " .. row.input, key == row.key or (key == nil and reason == "unsupported_unicode_key"))
end
for _, row in ipairs(contract.files) do
    local size = tonumber(row.name:match("hash_(%d+)%.bin"))
    local text = ("0123456789abcdef"):rep(math.ceil(size / 16)):sub(1, size)
    local file = assert(io.open(home .. "/hash.bin", "wb")); file:write(text); file:close()
    check("Dropbox hash file/JVM/" .. size, Hash.file(home .. "/hash.bin") == row.hash)
    check("Dropbox hash memory/JVM/" .. size, Hash.text(text) == row.hash)
end
local cre = require("libs/libkoreader-cre")
cre.initCache(home .. "/engine-cache", 8 * 1024 * 1024)
cre.registerFont("fonts/freefont/FreeSans.ttf")
local manifest = json.decode(raw("positions.json"))
for _, fixture in ipairs(manifest.fixtures) do
    local filename = home .. "/" .. fixture.name .. ".txt"
    local file = assert(io.open(filename, "wb")); file:write(fixture.text); file:close()
    for _, size in ipairs{ 18, 32 } do
        local doc = cre.newDocView(600, 800, 0)
        doc:setIntProperty("crengine.file.txt.preformatted", 1)
        doc:setIntProperty("crengine.render.requested_dom_version", cre.getLatestDomVersion())
        doc:setStringProperty("font.face.default", "FreeSans")
        doc:setFontSize(size)
        assert(doc:loadDocument(filename)); doc:renderDocument()
        local map = Map.build(doc, fixture.text)
        check(fixture.name .. "/supported/" .. size, (map ~= nil) == fixture.expected_supported)
        if map then
            for _, sample in ipairs(fixture.samples) do
                local xp = map:to_xpointer(sample.offset)
                local prefix, point
                if xp then prefix, point = xp:match("^(.*)%.(%d+)$") end
                local selected = prefix and doc:getTextFromXPointers(xp, prefix .. "." .. (tonumber(point) + 1))
                check(fixture.name .. "/character/" .. size .. "/" .. sample.offset,
                    xp and map:from_xpointer(xp) == sample.offset and selected == sample.character)
            end
            local eof = map:to_exact_boundary(map.length)
            check(fixture.name .. "/logical EOF/" .. size, eof and map:from_xpointer(eof) == map.length)
        end
        doc:close()
    end
end
local output = assert(io.open(home .. "/parity-results.json", "wb"))
output:write(json.encode{ total = total, passed = total - failed, failed = failed, skipped = 0,
    scope = "Product modules against captured actual Desktop Kotlin oracles and real crengine text ranges" })
output:close()
print(string.format("PRODUCT_PARITY total=%d passed=%d failed=%d", total, total - failed, failed))
os.exit(failed == 0 and 0 or 1)
