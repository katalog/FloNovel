require("setupkoenv")
local cre, json = require("libs/libkoreader-cre"), require("json")
local Map = dofile("../../../../experiments/txt_position_map.lua")
local root = "../../../../.work/results/scale-probe"
local function read(name)
    local file = assert(io.open(root .. "/" .. name, "rb"))
    local text = file:read("*a"); file:close(); return text
end
local manifest = json.decode(read("manifest.json"))
-- Match CreDocument's default storage factor instead of the binding's low-memory default.
cre.initCache(root .. "/cache", 16 * 1024 * 1024, true, 40)
cre.registerFont("fonts/freefont/FreeSans.ttf")
local output = { tests = manifest.source_checks, goldens = {}, scale = {},
    scope = "PC_engine_public_goldens_and_synthetic_scale_not_Kindle" }
local function check(name, success, detail)
    output.tests[#output.tests + 1] = { name = name, passed = not not success, detail = detail }
end
local function resident_kib()
    local file = assert(io.open("/proc/self/status", "rb"))
    local value = tonumber(file:read("*a"):match("VmRSS:%s+(%d+)")); file:close(); return value
end
local function open(name, size)
    local doc = cre.newDocView(600, 800, 1)
    doc:setIntProperty("crengine.file.txt.preformatted", 1)
    doc:setIntProperty("crengine.render.requested_dom_version", cre.getLatestDomVersion())
    doc:setStringProperty("font.face.default", "FreeSans")
    doc:setFontSize(size)
    assert(doc:loadDocument(root .. "/" .. name))
    doc:renderDocument()
    return doc
end
local function verify_samples(doc, map, samples)
    local failures, details = 0, {}
    for _, sample in ipairs(samples) do
        local xp = map:to_xpointer(sample.offset)
        local restored = xp and map:from_xpointer(xp)
        local prefix, point
        if xp then prefix, point = xp:match("^(.*)%.(%d+)$") end
        local actual = prefix and doc:getTextFromXPointers(xp, prefix .. "." .. (tonumber(point) + 1))
        if actual ~= sample.character or restored ~= sample.offset then
            failures = failures + 1
            if #details < 4 then details[#details + 1] = { offset = sample.offset, actual = actual, restored = restored } end
        end
    end
    return failures == 0, details
end
for _, fixture in ipairs(manifest.goldens) do
    local row = { name = fixture.name, bytes_sha256 = fixture.sha256, sample_count = #fixture.samples }
    for _, size in ipairs({ 18, 32 }) do
        local doc = open(fixture.name, size)
        local map, reason = Map.build(doc, fixture.text)
        local label = fixture.name .. "/font_" .. size
        if #fixture.text == 0 then
            check(label .. "/empty_is_not_mapped", map == nil and reason == "no_visible_text")
        else
            check(label .. "/strict_mapping", map ~= nil, reason)
            if map then
                check(label .. "/utf16_length", map.length == fixture.utf16_units)
                local matched, details = verify_samples(doc, map, fixture.samples)
                check(label .. "/reference_character_samples", matched, details)
            end
        end
        row.supported, row.reason = map ~= nil, reason
        doc:close()
    end
    output.goldens[#output.goldens + 1] = row
end
for _, fixture in ipairs(manifest.large) do
    collectgarbage("collect")
    local before_rss, before_open = resident_kib(), os.clock()
    local doc = open(fixture.name, 20)
    local row = { name = fixture.name, bytes = fixture.bytes, source_units = fixture.utf16_units,
        storage_factor = 40, native_cache_state = "may_be_warm", timing = "process_cpu_seconds",
        open_render_cpu_seconds = os.clock() - before_open, process_rss_after_render_kib = resident_kib(),
        process_rss_before_open_kib = before_rss }
    local source = read(fixture.name)
    collectgarbage("collect")
    local before_heap, before_map = collectgarbage("count"), os.clock()
    local map, reason = Map.build(doc, source)
    row.map_cpu_seconds, row.lua_heap_growth_kib = os.clock() - before_map, collectgarbage("count") - before_heap
    row.process_rss_after_mapping_kib, row.pages = resident_kib(), doc:getPages()
    check(fixture.name .. "/strict_mapping", map ~= nil, reason)
    if map then
        row.nodes = #map.nodes
        check(fixture.name .. "/utf16_length", map.length == fixture.utf16_units)
        local before_query = os.clock()
        local matched, details = verify_samples(doc, map, fixture.samples)
        row.sample_queries_cpu_seconds = os.clock() - before_query
        row.sample_queries = #fixture.samples
        check(fixture.name .. "/distributed_samples", matched, details)
    end
    output.scale[#output.scale + 1] = row
    doc:close()
end
output.total, output.failed = #output.tests, 0
for _, result in ipairs(output.tests) do if not result.passed then output.failed = output.failed + 1 end end
output.passed = output.total - output.failed
local file = assert(io.open(root .. "/scale-results.json", "wb"))
file:write(json.encode(output)); file:close()
print(string.format("GOLDEN_SCALE_CHECKS total=%d passed=%d failed=%d", output.total, output.passed, output.failed))
os.exit(output.failed == 0 and 0 or 1)
