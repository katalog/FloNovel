-- A bounded, in-memory prototype. UI lifecycle invalidation is not wired yet.
local Map = dofile("../../../../experiments/txt_position_map.lua")
local json, sha = require("json"), require("ffi/sha2")
local Cache = {}
Cache.__index = Cache

function Cache.new()
    return setmetatable({ builds = 0, hits = 0 }, Cache)
end

function Cache:clear()
    self.entry = nil
end

function Cache:get(doc, source, context)
    local required = { "engine_version", "mapping_version", "encoding", "library_scope", "account_scope", "book_key", "generation" }
    local values = {}
    for _, name in ipairs(required) do
        if context[name] == nil then self:clear(); return nil, "missing_context_" .. name end
        values[#values + 1] = context[name]
    end
    local mode = doc:getIntProperty("crengine.file.txt.preformatted")
    if mode ~= 1 then self:clear(); return nil, "unsupported_parser_mode" end
    values[#values + 1] = mode
    values[#values + 1] = doc:getIntProperty("crengine.render.requested_dom_version")
    values[#values + 1] = context.known_line_changes == true
    local key = sha.sha256(source) .. ":" .. sha.sha256(json.encode(values))
    if self.entry and self.entry.doc == doc and self.entry.key == key then
        self.hits = self.hits + 1
        return self.entry.map, "hit"
    end
    -- Never retain the old map under a new identity when rebuilding is rejected.
    self:clear()
    local map, reason = Map.build(doc, source, { known_line_changes = context.known_line_changes == true })
    if not map then return nil, "rejected_" .. reason end
    self.builds = self.builds + 1
    self.entry = { doc = doc, key = key, map = map }
    return map, "rebuilt"
end

return Cache
