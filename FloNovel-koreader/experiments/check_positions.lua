-- Check an experimental mapping against independently generated UTF-16 samples.
require("setupkoenv")
local cre = require("libs/libkoreader-cre")
local json = require("json")
local project = "../../../.."
local Map = dofile(project .. "/experiments/txt_position_map.lua")
local root = project .. "/.work/results/position-probe"
local file = assert(io.open(root .. "/manifest.json", "rb"))
local manifest = json.decode(file:read("*a"))
file:close()
cre.initCache(root .. "/cache", 8 * 1024 * 1024)
cre.registerFont("fonts/freefont/FreeSans.ttf")
local output = { runtime = manifest.runtime, dom_version = cre.getLatestDomVersion(), tests = {},
    documents = {}, scope = "engine_coordinates_only_not_ReaderRolling_or_visual_verification" }

local function check(name, success, detail)
    output.tests[#output.tests + 1] = { name = name, passed = not not success, detail = detail }
end

for _, invalid in ipairs({ "\255", "\192\128", "\237\160\128", "\244\144\128\128", "\226\130" }) do
    local map, reason = Map.build(nil, invalid)
    check("reject_invalid_utf8_" .. #output.tests, map == nil and reason == "invalid_utf8")
end
local nul_map, nul_reason = Map.build(nil, "A\0B")
check("reject_nul", nul_map == nil and nul_reason == "unsupported_nul")

for _, fixture in ipairs(manifest.fixtures) do
    local result = { name = fixture.name, checks = 0, geometry_mismatches = 0, bookmark_mismatches = 0 }
    for _, size in ipairs({ 18, 32 }) do
        for _, mode in ipairs({ 0, 1 }) do
            local doc = cre.newDocView(600, 800, mode)
            doc:setIntProperty("crengine.file.txt.preformatted", 1)
            doc:setIntProperty("crengine.render.requested_dom_version", cre.getLatestDomVersion())
            doc:setStringProperty("font.face.default", "FreeSans")
            doc:setFontSize(size)
            local loaded = doc:loadDocument(root .. "/" .. fixture.name .. ".txt")
            local label = fixture.name .. "/font_" .. size .. "/mode_" .. mode
            check(label .. "/load", loaded)
            if loaded then
                doc:renderDocument()
                local map, reason = Map.build(doc, fixture.text)
                check(label .. "/support_classification", (map ~= nil) == fixture.expected_supported, reason)
                result.supported, result.reason = map ~= nil, reason
                if not fixture.expected_supported then
                    check(label .. "/rejection_reason", reason and reason:find(fixture.expected_rejection, 1, true) == 1, reason)
                end
                if map then
                    check(label .. "/utf16_length", map.length == fixture.utf16_units)
                    local geometry_before = result.geometry_mismatches
                    local failures, details = 0, {}
                    for _, sample in ipairs(fixture.samples) do
                        local xp, err = map:to_xpointer(sample.offset)
                        local restored = xp and map:from_xpointer(xp)
                        local prefix, point
                        if xp then prefix, point = xp:match("^(.*)%.(%d+)$") end
                        -- This is a real engine text-range read, independent of the Lua round trip.
                        local selected = prefix and doc:getTextFromXPointers(xp, prefix .. "." .. (tonumber(point) + 1))
                        local correct = restored == sample.offset and selected == sample.character
                        result.checks = result.checks + 1
                        if not correct then
                            failures = failures + 1
                            if #details < 5 then details[#details + 1] = { offset = sample.offset, xp = xp,
                                restored = restored, selected = selected, expected = sample.character, error = err } end
                        end
                        if xp then
                            local target_y = doc:getPosFromXPointer(xp)
                            local target_page = doc:getPageFromXPointer(xp)
                            doc:gotoXPointer(xp)
                            local visible = mode == 1 and doc:getCurrentPage() == target_page
                                or mode == 0 and target_y >= doc:getCurrentPos() and target_y < doc:getCurrentPos() + 800
                            if not visible then result.geometry_mismatches = result.geometry_mismatches + 1 end
                            if map:from_xpointer(doc:getXPointer()) ~= sample.offset then
                                result.bookmark_mismatches = result.bookmark_mismatches + 1
                            end
                        end
                    end
                    check(label .. "/roundtrip_and_engine_character", failures == 0, details)
                    check(label .. "/target_within_engine_view", result.geometry_mismatches == geometry_before)
                    check(label .. "/reject_negative", map:to_xpointer(-1) == nil)
                    check(label .. "/reject_past_end", map:to_xpointer(map.length + 1) == nil)
                    check(label .. "/reject_fractional", map:to_xpointer(0.5) == nil)
                    check(label .. "/reject_missing_xpointer", map:from_xpointer(nil) == nil)
                    for _, offset in ipairs(fixture.surrogate_interiors) do
                        check(label .. "/reject_surrogate_" .. offset, map:to_xpointer(offset) == nil)
                    end
                    for _, offset in ipairs(fixture.hidden_offsets) do
                        check(label .. "/reject_hidden_" .. offset, map:to_xpointer(offset) == nil)
                    end
                end
            end
            doc:close()
        end
    end
    output.documents[#output.documents + 1] = result
end

output.total, output.failed = #output.tests, 0
for _, result in ipairs(output.tests) do
    if not result.passed then output.failed = output.failed + 1 end
end
output.passed = output.total - output.failed
file = assert(io.open(root .. "/mapping-results.json", "wb"))
file:write(json.encode(output))
file:close()
print(string.format("POSITION_CHECKS total=%d passed=%d failed=%d", output.total, output.passed, output.failed))
os.exit(output.failed == 0 and 0 or 1)
