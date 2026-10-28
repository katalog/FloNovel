local json = require("json")
local ltn12 = require("ltn12")
local socket_http = require("socket.http")
local HTTP = {}

function HTTP.request(method, url, headers, body, output)
    local parsed = type(url) == "string" and require("socket.url").parse(url)
    if not parsed or parsed.scheme ~= "https" or not parsed.host or not parsed.host:match("^[a-zA-Z0-9.-]+$")
        or parsed.userinfo or parsed.fragment or url:find("[\r\n]")
        or (parsed.port and (not tonumber(parsed.port) or tonumber(parsed.port) < 1 or tonumber(parsed.port) > 65535)) then
        return nil, "invalid_https_url"
    end
    -- Network support must not prevent the independent local chapter feature
    -- from loading on a runtime with missing TLS libraries or symbols.
    local loaded, TLS = pcall(require, "flonovel.tls")
    if not loaded then return nil, "tls_unavailable" end
    local response, size, overflow = {}, 0, false
    local sink = output or function(chunk)
        if chunk then
            size = size + #chunk
            if size > 4 * 1024 * 1024 then overflow = true; return nil, "response_too_large" end
            response[#response + 1] = chunk
        end
        return 1
    end
    local request_headers = { ["Connection"] = "close" }
    for key, value in pairs(headers or {}) do request_headers[key] = value end
    if body then request_headers["Content-Length"] = tostring(#body) end
    local ok, result, code, response_headers = pcall(socket_http.request, {
        url = url, method = method, headers = request_headers,
        source = body and ltn12.source.string(body), sink = sink,
        redirect = false, create = TLS.create("data/ca-bundle.crt"),
    })
    -- Raw exception/server bodies may echo credentials. Only bounded codes leave
    -- this boundary; callers keep typed failure reasons without sensitive text.
    if not ok or not result then return nil, overflow and "response_too_large" or "network_or_tls_failed" end
    code = tonumber(code)
    if not code or code < 200 or code >= 300 then return nil, "http_" .. tostring(code or "invalid") end
    return { body = table.concat(response), headers = response_headers or {}, status = code }
end

function HTTP.json(method, url, headers, data)
    local response, reason = HTTP.request(method, url, headers, data and json.encode(data))
    if not response then return nil, reason end
    if response.body == "" then return {}, nil, response end
    local ok, value = pcall(json.decode, response.body)
    if not ok or type(value) ~= "table" then return nil, "invalid_json_response" end
    return value, nil, response
end

return HTTP
