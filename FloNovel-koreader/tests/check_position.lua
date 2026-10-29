require("setupkoenv")
local project, home = assert(os.getenv("FLONOVEL_PROJECT")), assert(os.getenv("KO_HOME"))
package.path = project .. "/flonovel.koplugin/?.lua;" .. package.path
local Position = require("flonovel.reading_position")
local State = require("flonovel.state")
local Hash = require("flonovel.content_hash")
local Paths = require("flonovel.paths")
local json = require("json")
local total, failed = 0, 0
local function check(name, value)
    total = total + 1
    if not value then failed = failed + 1; print("FAIL " .. name) end
end
local config = { account_id = "synthetic-account", library_root = Paths.root(home),
    supabase_url = "https://synthetic.supabase.co", publishable_key = "synthetic-key",
    shared_secret = "synthetic-secret", position_verified = true }
local requests, remote, reject = {}, nil, false
local transport = { json = function(method, url, headers, data)
    requests[#requests + 1] = { method = method, url = url, headers = headers, data = data }
    if reject then return nil, "http_503" end
    if method == "GET" then return remote and { remote } or {} end
    remote = { char_offset = math.max(data.char_offset, remote and remote.char_offset or 0), source = data.source, encoding = data.encoding }
    return {}
end }
local client = Position.new(config, transport)
check("push succeeds", client:push("book.txt", 123))
check("push sends one POST without GET", #requests == 1 and requests[1].method == "POST")
local request = requests[1]
check("table endpoint", request.url == "https://synthetic.supabase.co/rest/v1/flonovel_sync")
check("apikey required", request.headers.apikey == "synthetic-key")
check("secret header required", request.headers["x-flonovel-secret"] == "synthetic-secret")
check("Authorization forbidden", request.headers.Authorization == nil and request.headers.authorization == nil)
check("user_key forbidden", request.data.user_key == nil)
check("merge-duplicates upsert", request.headers.Prefer == "resolution=merge-duplicates")
check("source is candidate koreader", request.data.source == "koreader")
check("UTF16 offset unchanged", request.data.char_offset == 123)
check("encoding matches decoded UTF8", request.data.encoding == "UTF-8")
check("negative offset rejected", client:push("book.txt", -1) == nil)
check("fractional offset rejected", client:push("book.txt", 1.5) == nil)
check("noncanonical key rejected", client:push("BOOK.TXT", 1) == nil)
local fetched = client:fetch("한 글.txt")
check("fetch URL percent-encodes path", requests[#requests].url:lower():find("%ed%95%9c%20%ea%b8%80%2etxt", 1, true) ~= nil)
check("fetch returns remote offset", fetched.char_offset == 123)
remote = nil
check("missing row distinguished from error", client:fetch("book.txt").absent)
check("source and max-wins verification", client:verify())

local queue = home .. "/position-test.json"
check("durable enqueue", client:enqueue(queue, "book.txt", 12, Hash.file(home .. "/book.txt")))
check("queue stores unsent request", State.read(queue).jobs["book.txt"].offset == 12)
reject = true
local report = client:flush(queue)
check("failed send remains retryable", report and #report.deferred == 1 and State.read(queue).jobs["book.txt"] ~= nil)
reject = false
report = client:flush(queue)
check("successful retry acknowledged", report and report.sent == 1 and next(State.read(queue).jobs) == nil)
check("empty queue sends nothing", client:flush(queue).sent == 0)
client:enqueue(queue, "book.txt", 20)
client:enqueue(queue, "book.txt", 5)
check("newer pending request replaces older anchor", State.read(queue).jobs["book.txt"].offset == 5)
local file = assert(io.open(home .. "/book.txt", "wb")); file:write("changed body"); file:close()
report = client:flush(queue)
check("changed body blocks obsolete queue", report.sent == 0 and #report.deferred == 1)
check("expected body mismatch blocks enqueue", client:enqueue(queue, "book.txt", 30, "incorrect-hash") == nil)
config.position_verified = false
check("unverified live source cannot auto-send", client:flush(queue) == nil)
config.position_verified = true
config.account_id = "different"
check("queue cannot cross accounts", client:flush(queue) == nil)
config.account_id = "synthetic-account"
config.library_root = home .. "/different"
check("queue cannot cross library roots", client:flush(queue) == nil)
config.library_root = Paths.root(home)
config.shared_secret = "different-partition"
check("queue cannot cross secret partitions", client:flush(queue) == nil)
config.shared_secret = "synthetic-secret"
local broken = home .. "/position-broken.json"
file = assert(io.open(broken, "wb")); file:write("not JSON"); file:close()
check("queue corruption never becomes empty queue", client:flush(broken) == nil)
local Fast = require("flonovel.fast_position")
check("bundled Unicode backend accepts scalar/surrogate vectors", Fast.supported)
local fast_file = home .. "/fast-position.txt"
local function fast_source(text)
    local source = assert(io.open(fast_file, "wb")); source:write(text); source:close()
end
fast_source("\239\187\191## A\r\n\r\n한글😀 unique\r\n")
local located = Fast.locate(fast_file, { text = "한글😀 unique", ordinal = 2, local_units = 2 })
check("fast search preserves BOM/CRLF/Hangul UTF16 offset", located and located.offset == 10)
local reversed = Fast.reverse(fast_file, 10)
check("fast remote offset resolves native scalar point", reversed and reversed.xp == "/FictionBook/body/pre[2]/text().2")
check("fast reverse rejects surrogate interior", Fast.reverse(fast_file, 11) == nil)
check("fast reverse rejects CRLF interior", Fast.reverse(fast_file, 7) == nil)
check("fast search checks source ordinal", Fast.locate(fast_file, { text = "한글😀 unique", ordinal = 1, local_units = 0 }) == nil)
fast_source("same\nsame\n")
check("fast search holds duplicate lines", Fast.locate(fast_file, { text = "same", ordinal = 1, local_units = 0 }) == nil)
fast_source(string.rep("x", 1100) .. "\nunique\n")
check("fast search holds unsupported preceding long lines", Fast.locate(fast_file, { text = "unique", ordinal = 2, local_units = 0 }) == nil)
fast_source("unique\n\255")
check("fast search validates UTF8 beyond the target", Fast.locate(fast_file, { text = "unique", ordinal = 1, local_units = 0 }) == nil)
fast_source("unique\n\0")
check("fast search rejects NUL", Fast.locate(fast_file, { text = "unique", ordinal = 1, local_units = 0 }) == nil)
local output = assert(io.open(home .. "/position-results.json", "wb"))
output:write(json.encode{ total = total, passed = total - failed, failed = failed, skipped = 0,
    scope = "Synthetic transport; actual Supabase source/trigger acceptance remains unverified" })
output:close()
print(string.format("PRODUCT_POSITION total=%d passed=%d failed=%d", total, total - failed, failed))
os.exit(failed == 0 and 0 or 1)
