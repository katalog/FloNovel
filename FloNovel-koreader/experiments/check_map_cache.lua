require("setupkoenv")
local cre, json = require("libs/libkoreader-cre"), require("json")
local Cache = dofile("../../../../experiments/map_cache_probe.lua")
local root = "../../../../.work/results/scale-probe"
local file = assert(io.open(root .. "/manifest.json", "rb"))
local manifest = json.decode(file:read("*a")); file:close()
cre.initCache(root .. "/cache", 8 * 1024 * 1024)
cre.registerFont("fonts/freefont/FreeSans.ttf")
local output = { tests = {}, scope = "in_memory_cache_prototype_not_product_lifecycle" }
local function check(name, success, detail)
    output.tests[#output.tests + 1] = { name = name, passed = not not success, detail = detail }
end
local function open(name)
    local doc = cre.newDocView(600, 800, 0)
    doc:setIntProperty("crengine.file.txt.preformatted", 1)
    doc:setIntProperty("crengine.render.requested_dom_version", cre.getLatestDomVersion())
    doc:setStringProperty("font.face.default", "FreeSans")
    assert(doc:loadDocument(root .. "/" .. name)); doc:renderDocument(); return doc
end
local context = { engine_version = manifest.runtime, mapping_version = 1, encoding = "UTF-8",
    library_scope = "synthetic-library-a", account_scope = "synthetic-account-a", book_key = "cache.txt", generation = 1 }
local doc, cache = open("cache_a.txt"), Cache.new()
local initial, status = cache:get(doc, manifest.cache_text, context)
check("first_build", initial and status == "rebuilt")
local repeated, repeat_status = cache:get(doc, manifest.cache_text, context)
check("same_identity_reuses_map", repeated == initial and repeat_status == "hit")
doc:setFontSize(32); doc:renderDocument()
local reflowed, reflow_status = cache:get(doc, manifest.cache_text, context)
check("font_reflow_keeps_source_mapping", reflowed == initial and reflow_status == "hit")
local xp = reflowed and reflowed:to_xpointer(0)
check("reflowed_mapping_still_valid", xp and reflowed:from_xpointer(xp) == 0)
for _, field in ipairs({ "engine_version", "mapping_version", "encoding", "library_scope", "account_scope", "book_key", "generation", "known_line_changes" }) do
    local changed = {}
    for key, value in pairs(context) do changed[key] = value end
    if field == "known_line_changes" then changed[field] = true
    elseif type(changed[field]) == "number" then changed[field] = changed[field] + 1
    else changed[field] = changed[field] .. "-changed" end
    local previous = cache.entry and cache.entry.map
    local rebuilt, reason = cache:get(doc, manifest.cache_text, changed)
    check("identity_change_" .. field, rebuilt and rebuilt ~= previous and reason == "rebuilt", reason)
end
local changed_source = manifest.cache_text:gsub("Before", "Xefore")
local rejected, rejection = cache:get(doc, changed_source, context)
check("same_size_source_change_is_not_a_hit", rejected == nil and rejection:match("^rejected_") and cache.entry == nil, rejection)
assert(doc:loadDocument(root .. "/cache_b.txt")); doc:renderDocument()
local updated, updated_status = cache:get(doc, changed_source, context)
check("changed_source_and_dom_rebuild", updated and updated_status == "rebuilt")
doc:setIntProperty("crengine.file.txt.preformatted", 0)
local incompatible, incompatible_status = cache:get(doc, changed_source, context)
check("parser_mode_change_rejects_stale_cache", incompatible == nil and incompatible_status == "unsupported_parser_mode" and cache.entry == nil)
doc:close()
local reopened = open("cache_a.txt")
local fresh, fresh_status = cache:get(reopened, manifest.cache_text, context)
check("new_document_builds_fresh_map", fresh and fresh_status == "rebuilt")
cache:clear()
check("explicit_close_invalidation", cache.entry == nil)
local missing = {}; for key, value in pairs(context) do missing[key] = value end; missing.generation = nil
local absent, absent_status = cache:get(reopened, manifest.cache_text, missing)
check("missing_generation_rejected", absent == nil and absent_status == "missing_context_generation")
reopened:close()
output.total, output.failed = #output.tests, 0
for _, result in ipairs(output.tests) do if not result.passed then output.failed = output.failed + 1 end end
output.passed, output.builds, output.hits = output.total - output.failed, cache.builds, cache.hits
file = assert(io.open(root .. "/cache-results.json", "wb")); file:write(json.encode(output)); file:close()
print(string.format("CACHE_CHECKS total=%d passed=%d failed=%d", output.total, output.passed, output.failed))
os.exit(output.failed == 0 and 0 or 1)
