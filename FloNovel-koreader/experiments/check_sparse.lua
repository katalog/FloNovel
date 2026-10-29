require("setupkoenv")
io.stdout:setvbuf("line")
local project, home = assert(os.getenv("FLONOVEL_PROJECT")), assert(os.getenv("KO_HOME"))
package.path = project .. "/flonovel.koplugin/?.lua;" .. package.path
local Sparse = dofile(project .. "/experiments/sparse_position_map.lua")
local cre, json = require("libs/libkoreader-cre"), require("json")
local clock = require("socket").gettime
local function read(path)
    local file = assert(io.open(path, "rb")); local value = file:read("*a"); file:close(); return value
end
local function write(path, text)
    local file = assert(io.open(path, "wb")); assert(file:write(text)); assert(file:close())
end
local total, failed, rows = 0, 0, {}
local function check(name, value)
    total = total + 1
    if not value then failed = failed + 1; print("FAIL " .. name) end
end
local function distribution(values)
    table.sort(values)
    return { count = #values, p50_ms = values[math.ceil(#values * .5)] * 1000,
        p95_ms = values[math.ceil(#values * .95)] * 1000, max_ms = values[#values] * 1000 }
end
cre.initCache(home .. "/cache", 16 * 1024 * 1024, true, 40)
cre.registerFont("fonts/freefont/FreeSans.ttf")
local manifest = json.decode(read(home .. "/manifest.json"))
for _, fixture in ipairs(manifest) do
    local path = home .. "/" .. fixture.name .. ".txt"
    local doc = cre.newDocView(600, 800, 1)
    doc:setIntProperty("crengine.file.txt.preformatted", 1)
    doc:setIntProperty("crengine.render.requested_dom_version", cre.getLatestDomVersion())
    doc:setStringProperty("font.face.default", "FreeSans")
    doc:setFontSize(22)
    local start = clock()
    assert(doc:loadDocument(path)); doc:renderDocument()
    local row = { name = fixture.name, bytes = fixture.bytes, render_wall_seconds = clock() - start }
    print("SPARSE_START " .. fixture.name)
    start = clock()
    local saved, reason, reference = Sparse.prepare(doc, path)
    row.prepare_wall_seconds = clock() - start
    if fixture.unsupported then
        check(fixture.name .. "/unsupported held", saved == nil)
        row.reason = reason
    else
        check(fixture.name .. "/prepared", saved ~= nil)
        assert(saved, reason)
        local serialized = json.encode(saved)
        row.index_bytes, row.checkpoints = #serialized, #saved.payload[10]
        write(home .. "/" .. fixture.name .. ".index.json", serialized)
        start = clock()
        local loaded = json.decode(read(home .. "/" .. fixture.name .. ".index.json"))
        local index = assert(Sparse.open(doc, path, loaded))
        row.reload_and_source_validation_wall_seconds = clock() - start
        local send_times, receive_times, max_error, max_send_read, max_receive_read = {}, {}, 0, 0, 0
        for _, sample in ipairs(fixture.samples) do
            local before = index.bytes_read
            start = clock()
            local actual, error = index:from_xpointer(sample.xp)
            send_times[#send_times + 1] = clock() - start
            max_send_read = math.max(max_send_read, index.bytes_read - before)
            check(fixture.name .. "/source offset/" .. sample.xp, actual == sample.offset)
            if actual then max_error = math.max(max_error, math.abs(actual - sample.offset)) end
            before = index.bytes_read
            start = clock()
            local xp, failure = index:to_xpointer(sample.offset)
            receive_times[#receive_times + 1] = clock() - start
            max_receive_read = math.max(max_receive_read, index.bytes_read - before)
            check(fixture.name .. "/reverse roundtrip/" .. sample.offset, xp and index:from_xpointer(xp) == sample.offset)
        end
        row.send, row.receive = distribution(send_times), distribution(receive_times)
        row.max_utf16_error, row.max_send_read_bytes, row.max_receive_read_bytes = max_error, max_send_read, max_receive_read
        row.max_utf8_error_bytes = max_error == 0 and 0 or nil
        local native_times = {}
        for step = 0, 11 do
            doc:gotoPage(math.max(1, math.floor(doc:getPages() * step / 12)))
            local xp = doc:getXPointer()
            local expected = reference:from_xpointer(xp)
            start = clock()
            local actual = index:from_xpointer(xp)
            native_times[#native_times + 1] = clock() - start
            check(fixture.name .. "/native page anchor/" .. step, actual ~= nil and actual == expected)
        end
        row.native_page_send = distribution(native_times)
        check(fixture.name .. "/invalid local point rejected", index:from_xpointer("/FictionBook/body/pre[1]/text().999999") == nil)
        check(fixture.name .. "/negative offset rejected", index:to_xpointer(-1) == nil)
        local eof = assert(index:to_xpointer(fixture.length))
        check(fixture.name .. "/EOF", index:from_xpointer(eof) == fixture.length)
        local damaged = json.decode(serialized); damaged.payload[10][1][2] = 99
        check(fixture.name .. "/corrupt index rejected", Sparse.open(doc, path, damaged) == nil)
        local source = read(path)
        write(path, source .. "changed")
        check(fixture.name .. "/changed source rejected", Sparse.open(doc, path, loaded) == nil)
        write(path, source)
        doc:setFontSize(24); doc:renderDocument()
        check(fixture.name .. "/changed rendering rejected", index:from_xpointer(fixture.samples[1].xp) == nil)
        index:close()
    end
    rows[#rows + 1] = row
    write(home .. "/partial-results.json", json.encode(rows))
    print("SPARSE_CASE " .. json.encode(row))
    doc:close()
    collectgarbage("collect")
end
local report = { total = total, passed = total - failed, failed = failed, skipped = 0, cases = rows,
    cpu_percent = tonumber(os.getenv("FLONOVEL_SPARSE_QUOTA")), runtime = "v2026.07.1",
    scope = "Synthetic prepared sparse index; actual PC engine, interpreter, optional CPU quota; not Kindle",
    preparation = "Full current mapper validation once, then compact source checkpoints; not source-only indexing during download",
    query = "Prepared and source-validated index in memory; OS file cache may be warm; whole-book source revalidation excluded from query timing",
    live_services_tested = false, device_tested = false }
write(home .. "/summary.json", json.encode(report))
print("SPARSE_TOTAL " .. json.encode({ total = total, passed = total - failed, failed = failed }))
os.exit(failed == 0 and 0 or 1)
