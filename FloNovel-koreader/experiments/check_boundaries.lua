-- Compare explicit diagnostic candidates without changing the strict mapper's policy.
require("setupkoenv")
local cre, json = require("libs/libkoreader-cre"), require("json")
local root = "../../../../.work/results/position-probe"
local Map = dofile("../../../../experiments/txt_position_map.lua")
local file = assert(io.open(root .. "/manifest.json", "rb"))
local manifest = json.decode(file:read("*a"))
file:close()
cre.initCache(root .. "/cache", 8 * 1024 * 1024)
cre.registerFont("fonts/freefont/FreeSans.ttf")
local output = { tests = {}, observations = {}, scope = "diagnostic_candidates_not_product_boundary_policy" }
local function check(name, success, detail)
    output.tests[#output.tests + 1] = { name = name, passed = not not success, detail = detail }
end
for _, fixture in ipairs(manifest.fixtures) do
    local doc = cre.newDocView(600, 800, 0)
    doc:setIntProperty("crengine.file.txt.preformatted", 1)
    doc:setIntProperty("crengine.render.requested_dom_version", cre.getLatestDomVersion())
    doc:setStringProperty("font.face.default", "FreeSans")
    assert(doc:loadDocument(root .. "/" .. fixture.name .. ".txt"))
    doc:renderDocument()
    local map, reason = Map.build(doc, fixture.text, { known_line_changes = true })
    if fixture.name:match("^separators") or fixture.name:match("^libru_header") then
        check(fixture.name .. "/candidate_built", map ~= nil, reason)
        if map then
            -- The final word follows the changed region. Its raw position must not drift.
            local offset = fixture.after_offset
            local xp = map:to_xpointer(offset)
            check(fixture.name .. "/following_text_offset", xp and map:from_xpointer(xp) == offset)
            local blocked, restored, mismatches = 0, 0, 0
            for _, sample in ipairs(fixture.samples) do
                local sample_xp = map:to_xpointer(sample.offset)
                if sample_xp then
                    local prefix, point = sample_xp:match("^(.*)%.(%d+)$")
                    local selected = doc:getTextFromXPointers(sample_xp, prefix .. "." .. (tonumber(point) + 1))
                    if map:from_xpointer(sample_xp) == sample.offset and selected == sample.character then
                        restored = restored + 1
                    else mismatches = mismatches + 1 end
                else blocked = blocked + 1 end
            end
            check(fixture.name .. "/unrepresented_positions_rejected", blocked > 0)
            check(fixture.name .. "/preserved_samples_match_engine", mismatches == 0, mismatches)
            for _, node in ipairs(map.nodes) do
                if node.blocked then
                    check(fixture.name .. "/changed_node_is_not_source_" .. node.index, map:from_xpointer(node.path .. ".0") == nil)
                end
            end
            output.observations[#output.observations + 1] = { name = fixture.name,
                restored_samples = restored, rejected_samples = blocked }
        end
    elseif fixture.expected_supported then
        check(fixture.name .. "/candidate_built", map ~= nil, reason)
    end
    if map and fixture.name:match("^eof_") then
        local ending = map:to_exact_boundary(map.length)
        local expected = fixture.name == "eof_plain"
        check(fixture.name .. "/exact_eof_availability", (ending ~= nil) == expected, ending)
        if ending then check(fixture.name .. "/eof_roundtrip", map:from_xpointer(ending) == map.length) end
        output.observations[#output.observations + 1] = { name = fixture.name, eof_exact = ending ~= nil }
    end
    if map then
        local exact, rejected = 0, 0
        for _, offset in ipairs(fixture.hidden_offsets) do
            local xp = map:to_exact_boundary(offset)
            if xp then
                check(fixture.name .. "/boundary_roundtrip_" .. offset, map:from_xpointer(xp) == offset)
                exact = exact + 1
            else rejected = rejected + 1 end
        end
        output.observations[#output.observations + 1] = { name = fixture.name, exact_hidden_boundaries = exact,
            rejected_hidden_boundaries = rejected }
    end
    doc:close()
end
output.total, output.failed = #output.tests, 0
for _, result in ipairs(output.tests) do if not result.passed then output.failed = output.failed + 1 end end
output.passed = output.total - output.failed
file = assert(io.open(root .. "/boundary-results.json", "wb"))
file:write(json.encode(output))
file:close()
print(string.format("BOUNDARY_CHECKS total=%d passed=%d failed=%d", output.total, output.passed, output.failed))
os.exit(output.failed == 0 and 0 or 1)
