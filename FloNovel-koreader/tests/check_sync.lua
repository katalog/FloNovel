require("setupkoenv")
local project = assert(os.getenv("FLONOVEL_PROJECT"))
local home = assert(os.getenv("KO_HOME"))
package.path = project .. "/flonovel.koplugin/?.lua;" .. package.path
local Dropbox = require("flonovel.dropbox")
local State = require("flonovel.state")
local Paths = require("flonovel.paths")
local Hash = require("flonovel.content_hash")
local sha = require("ffi/sha2")
local lfs = require("libs/libkoreader-lfs")
local json = require("json")
local total, failed = 0, 0
local function check(name, success)
    total = total + 1
    if not success then failed = failed + 1; print("FAIL " .. name) end
end
local function write(path, value)
    local file = assert(io.open(path, "wb")); assert(file:write(value)); assert(file:close())
end
local function hash(text) return sha.sha256(sha.hex_to_bin(sha.sha256(text))) end
local sequence = 0
local function fixture()
    sequence = sequence + 1
    local root = home .. "/library-" .. sequence
    assert(lfs.mkdir(root))
    local client = Dropbox.new{ account_id = "synthetic-account" }
    client.remote = { ["book.txt"] = { rev = "r1", hash = hash("## A\noriginal"), text = "## A\noriginal" } }
    client.list = function(self) return self.remote end
    client.download = function(self, path, metadata, target)
        write(target, metadata.text)
        return true
    end
    client.delete = function(self, path, metadata)
        self.deleted = (self.deleted or 0) + 1
        if self.reject_delete then return nil, "http_409" end
        assert(metadata.rev == self.remote[path].rev)
        self.remote[path] = nil
        return {}
    end
    return client, Paths.root(root), home .. "/state-" .. sequence .. ".json"
end
local client, root, path = fixture()
local report, reason = client:sync(path, root)
check("initial download", report and report.downloaded == 1)
check("downloaded content hash", Hash.file(root .. "/book.txt") == client.remote["book.txt"].hash)
check("base only after completed transfer", State.read(path).files["book.txt"].status == "SYNCED")
check("unchanged sync does not redownload", client:sync(path, root).downloaded == 0)
assert(os.remove(root .. "/book.txt"))
report = client:sync(path, root)
check("tracked individual deletion propagates", report and report.deleted_remote == 1 and not client.remote["book.txt"])
check("deletion removes base", next(State.read(path).files) == nil)

client, root, path = fixture()
client.remote["folder/nested.txt"] = { rev = "r2", hash = hash("nested"), text = "nested" }
report = client:sync(path, root)
check("new remote nested directories download", report and report.downloaded == 2)
assert(os.remove(root .. "/folder/nested.txt"))
assert(lfs.rmdir(root .. "/folder"))
report = client:sync(path, root)
check("missing ancestor is not individual deletion", report and report.deleted_remote == 0 and #report.deferred == 1)

client, root, path = fixture()
client:sync(path, root)
write(root .. "/book.txt", "local edit")
report = client:sync(path, root)
check("local edits preserved", report and #report.deferred == 1 and Hash.file(root .. "/book.txt") == hash("local edit"))
client.remote["book.txt"] = { rev = "r2", hash = hash("remote edit"), text = "remote edit" }
report = client:sync(path, root)
check("both modifications deferred without overwrite", report and report.downloaded == 0 and #report.deferred == 1)

client, root, path = fixture()
client:sync(path, root)
assert(os.remove(root .. "/book.txt"))
client.remote["book.txt"] = { rev = "r2", hash = hash("remote edit"), text = "remote edit" }
report = client:sync(path, root)
check("remote modification wins local deletion", report and report.downloaded == 1 and not client.deleted)

client, root, path = fixture()
report = client:sync(path, root, root .. "/book.txt")
check("open book protected even without reader features", report and report.downloaded == 0 and #report.deferred == 1)
check("open deferred file does not get a base", next(State.read(path).files) == nil)

client, root, path = fixture()
client:sync(path, root)
assert(os.remove(root .. "/book.txt"))
client.reject_delete = true
report = client:sync(path, root)
check("conditional delete rejection retains base", report and #report.deferred == 1 and State.read(path).files["book.txt"] ~= nil)
client.reject_delete = false
check("conditional delete retries", client:sync(path, root).deleted_remote == 1)

client, root, path = fixture()
client:sync(path, root)
assert(os.rename(root .. "/book.txt", root .. "/renamed.txt"))
report = client:sync(path, root)
check("unsupported local move cannot delete remote source", report and report.deleted_remote == 0 and not client.deleted)
client, root, path = fixture()
client:sync(path, root)
client.remote["renamed.txt"], client.remote["book.txt"] = client.remote["book.txt"], nil
report = client:sync(path, root)
check("unsupported remote move preserves original without retransmission", report and report.downloaded == 0 and report.recovered == 0 and #report.deferred == 2)

client, root, path = fixture()
client.remote = {}
report, reason = client:sync(path, root)
check("empty books stops sync", report and report.requires_confirmation and report.reason == "remote_books_empty_confirmation_required")

client, root, path = fixture()
for i = 1, 5 do client.remote[i .. ".txt"] = { rev = "r" .. i, hash = hash("file"), text = "file" } end
client:sync(path, root)
for i = 1, 5 do assert(os.remove(root .. "/" .. i .. ".txt")) end
report, reason = client:sync(path, root)
check("mass deletion stops before any request", report and report.requires_confirmation and report.reason == "mass_deletion_confirmation_required" and not client.deleted)
local signature = report.signature
client.remote["new.txt"] = { rev = "new", hash = hash("new"), text = "new" }
report = client:sync(path, root, nil, signature)
check("changed deletion plan requires new confirmation", report and report.requires_confirmation and not client.deleted)
report = client:sync(path, root, nil, report.signature)
check("confirmed same plan may proceed", report and report.deleted_remote == 5)
for _, boundary in ipairs{{ 20, 4, false }, { 17, 5, false }, { 16, 5, true }, { 100, 19, false }, { 100, 20, true }} do
    client, root, path = fixture()
    client.remote = {}
    local bases = {}
    for index = 1, boundary[1] do
        local name = index .. ".txt"
        client.remote[name] = { rev = "r" .. index, hash = hash("boundary"), text = "boundary" }
        bases[name] = { rev = "r" .. index, hash = hash("boundary"), status = "SYNCED" }
        if index > boundary[2] then write(root .. "/" .. name, "boundary") end
    end
    assert(State.write(path, { version = 1, account = client.config.account_id, scope = Dropbox.scope(client.config), root = root, files = bases }))
    report = client:sync(path, root)
    check("mass deletion boundary " .. boundary[2] .. "/" .. boundary[1],
        report and (not not report.requires_confirmation) == boundary[3]
        and (boundary[3] and not client.deleted or not boundary[3] and report.deleted_remote == boundary[2]))
end

client, root, path = fixture()
client:sync(path, root)
client.remote = { ["other.txt"] = { rev = "other", hash = hash("other"), text = "other" } }
report = client:sync(path, root)
check("remote deletion recoverable", report and report.recovered == 1 and not lfs.attributes(root .. "/book.txt"))
local recovery_count = 0
for name in lfs.dir(root .. "/.flonovel/recovery") do if name ~= "." and name ~= ".." then recovery_count = recovery_count + 1 end end
check("remote-deleted book retained in recovery", recovery_count == 1)

client, root, path = fixture()
client:sync(path, root)
client.config.account_id = "different-account"
report, reason = client:sync(path, root)
check("account mismatch blocks state reuse", not report and reason == "sync_scope_changed_reset_required")
client.config.account_id = "synthetic-account"
local root2 = root .. "-new"; assert(lfs.mkdir(root2))
report, reason = client:sync(path, root2)
check("library mismatch blocks deletion", not report and reason == "sync_scope_changed_reset_required")
client.config.app_key = "other-app"
report, reason = client:sync(path, root)
check("same account different Dropbox app is isolated", not report and reason == "sync_scope_changed_reset_required")
client.config.app_key = nil
client.config.dev = true
report, reason = client:sync(path, root)
check("development partition is isolated", not report and reason == "sync_scope_changed_reset_required")
client.config.dev = false
write(path, "broken JSON")
report, reason = client:sync(path, root)
check("corrupt state fails closed", not report and reason == "state_corrupt")

client, root, path = fixture()
client.download = function() return nil, "network_or_tls_failed" end
report = client:sync(path, root)
check("interruption records DOWNLOADING", report and State.read(path).files["book.txt"].status == "DOWNLOADING")
write(root .. "/book.txt", "user replacement")
report = client:sync(path, root)
check("interrupted transfer cannot overwrite new local content", report and #report.deferred == 1 and Hash.file(root .. "/book.txt") == hash("user replacement"))

check("NFC Hangul and separator key", Paths.key("Folder\\가.TXT") == "folder/가.txt")
check("full-width characters preserved", Paths.key("Ａ１.TXT") == "ａ１.txt")
check("sharp S does not casefold", Paths.key("Straße.TXT") == "straße.txt")
check("dotted I expands as JVM ROOT", Paths.key("İ.TXT") == "i̇.txt")
check("unverified contextual Sigma deferred", Paths.key("ΟΣ.TXT") == nil)
check("traversal rejected", Paths.relative("a/../book.txt") == nil)
check("absolute rejected", Paths.relative("/book.txt") == nil)
check("reserved subtree rejected", Paths.relative("folder/.flonovel/book.txt") == nil)
check("invalid UTF8 path rejected", Paths.key("\255.txt") == nil)
check("unassigned Unicode path deferred", Paths.key("\244\143\191\191.txt") == nil)

client, root, path = fixture()
client.remote["BOOK.TXT"] = { rev = "dup", hash = hash("other"), text = "other" }
-- API-level collision check uses the real list implementation.
local api = Dropbox.new({ account_id = "synthetic-account" }, {
    json = function(_, _, headers, body)
        check("Dropbox Bearer required", headers.Authorization == "Bearer synthetic-token")
        check("Dropbox root is app-relative", body.path == "/books")
        return { entries = {
            { [".tag"] = "file", path_display = "/books/A.txt", rev = "1", content_hash = hash("one") },
            { [".tag"] = "file", path_display = "/books/a.TXT", rev = "2", content_hash = hash("two") },
        }, has_more = false }
    end,
})
api.token = "synthetic-token"
local listed, error = api:list()
check("remote normalized collisions rejected", not listed and error == "remote_normalization_collision")
local fake_metadata = { rev = "synthetic-rev", hash = hash("downloaded") }
local downloader = Dropbox.new({}, { request = function(method, url, headers, body, sink)
    check("download uses content API", method == "POST" and url == "https://content.dropboxapi.com/2/files/download")
    check("download pins listed revision", json.decode(headers["Dropbox-API-Arg"]).path == "rev:synthetic-rev")
    check("download Bearer", headers.Authorization == "Bearer synthetic-token")
    assert(sink("downloaded")); assert(sink(nil))
    return { status = 200 }
end })
downloader.token = "synthetic-token"
check("streamed download verifies Dropbox hash", downloader:download("book.txt", fake_metadata, home .. "/download.part"))
fake_metadata.hash = hash("different")
check("mismatched download rejected before apply", downloader:download("book.txt", fake_metadata, home .. "/download.part") == nil)
local page_count = 0
api.http.json = function(_, url, _, data)
    page_count = page_count + 1
    if page_count == 1 then
        return { entries = {}, has_more = true, cursor = "memory-only-cursor" }
    end
    check("pagination uses continue endpoint", url:find("/files/list_folder/continue", 1, true) ~= nil)
    check("pagination cursor only from prior page", data.cursor == "memory-only-cursor")
    return { entries = {{ [".tag"] = "file", path_display = "/books/nested/book.txt", rev = "r1", content_hash = hash("page") }}, has_more = false }
end
listed = api:list()
check("pagination combines complete listing", listed and listed["nested/book.txt"] and page_count == 2)
local output = assert(io.open(home .. "/sync-results.json", "wb"))
output:write(json.encode{ total = total, passed = total - failed, failed = failed, skipped = 0,
    scope = "Product sync engine with synthetic remote and real local filesystem; no live account" })
output:close()
print(string.format("PRODUCT_SYNC total=%d passed=%d failed=%d", total, total - failed, failed))
os.exit(failed == 0 and 0 or 1)
