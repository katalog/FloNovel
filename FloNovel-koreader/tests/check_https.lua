require("setupkoenv")
local project, home = assert(os.getenv("FLONOVEL_PROJECT")), assert(os.getenv("KO_HOME"))
package.path = project .. "/flonovel.koplugin/?.lua;" .. package.path
local TLS = require("flonovel.tls")
local HTTP = require("flonovel.http")
local original = TLS.create
local ca = home .. "/ca.pem"
TLS.create = function() return original(ca) end
local port = assert(os.getenv("FLONOVEL_TLS_PORT"))
local url = "https://localhost:" .. port
local json = require("json")
local total, failed = 0, 0
local function check(name, value)
    total = total + 1
    if not value then failed = failed + 1; print("FAIL " .. name) end
end
local response = HTTP.json("POST", url .. "/wire", { apikey = "synthetic-key", ["x-flonovel-secret"] = "synthetic-secret" },
    { relative_path = "한 글.txt", char_offset = 13, source = "koreader", encoding = "UTF-8" })
check("verified HTTPS transport succeeds", response and response.char_offset == 13)
check("API key transmitted", response and response.headers.apikey == "synthetic-key")
check("shared secret transmitted", response and response.headers["x-flonovel-secret"] == "synthetic-secret")
check("Authorization absent on wire", response and not response.headers.authorization)
check("Unicode JSON arrives unchanged", response and response.relative_path == "한 글.txt")
local value, reason = HTTP.request("GET", "https://127.0.0.1:" .. port .. "/wrong-host", { Authorization = "Bearer synthetic-token" })
check("wrong hostname rejected", value == nil and reason == "network_or_tls_failed")
ca = home .. "/unrelated.pem"
value, reason = HTTP.request("GET", url .. "/untrusted", { Authorization = "Bearer synthetic-token" })
check("untrusted CA rejected", value == nil and reason == "network_or_tls_failed")
ca = home .. "/ca.pem"
value, reason = HTTP.request("GET", url .. "/redirect", { Authorization = "Bearer synthetic-token" })
check("redirect never followed", value == nil and reason == "http_302")
value, reason = HTTP.request("GET", url .. "/error")
check("response secrets excluded from errors", value == nil and reason == "http_503")
check("plaintext transport rejected", HTTP.request("GET", "http://localhost/wire") == nil)
check("userinfo URL rejected", HTTP.request("GET", "https://user:password@localhost/wire") == nil)
local output = assert(io.open(home .. "/https-results.json", "wb"))
output:write(json.encode{ total = total, passed = total - failed, failed = failed, skipped = 0,
    scope = "Real product HTTP/TLS transport against synthetic loopback certificates" })
output:close()
print(string.format("PRODUCT_HTTPS total=%d passed=%d failed=%d", total, total - failed, failed))
os.exit(failed == 0 and 0 or 1)
