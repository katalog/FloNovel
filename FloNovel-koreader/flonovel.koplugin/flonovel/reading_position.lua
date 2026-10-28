local Paths = require("flonovel.paths")
local State = require("flonovel.state")
local Hash = require("flonovel.content_hash")
local HTTP = require("flonovel.http")
local Position = {}
Position.__index = Position

local function offset_valid(offset)
    return type(offset) == "number" and offset >= 0 and offset <= 2147483647 and offset == math.floor(offset)
end

function Position.new(config, transport)
    return setmetatable({ config = config, http = transport or HTTP }, Position)
end

function Position.scope(config)
    return require("ffi/sha2").sha256(table.concat({ config.account_id or "", config.app_key or "",
        config.dev == true and "dev" or "production", config.supabase_url or "", config.shared_secret or "" }, "\0"))
end

function Position:headers()
    local c = self.config
    if type(c.supabase_url) ~= "string" or not c.supabase_url:match("^https://[a-zA-Z0-9.-]+/?$")
        or type(c.publishable_key) ~= "string" or c.publishable_key == ""
        or type(c.shared_secret) ~= "string" or c.shared_secret == "" then return nil, "position_not_configured" end
    return { apikey = c.publishable_key, ["x-flonovel-secret"] = c.shared_secret,
        ["Content-Type"] = "application/json", Prefer = "resolution=merge-duplicates" }
end

function Position:base()
    return self.config.supabase_url:gsub("/+$", "") .. "/rest/v1/flonovel_sync"
end

function Position:push(key, offset)
    if not offset_valid(offset) then return nil, "invalid_char_offset" end
    if key ~= "__connection_test__" and Paths.key(key) ~= key then return nil, "invalid_book_key" end
    local headers, reason = self:headers()
    if not headers then return nil, reason end
    -- No preflight GET and no client conflict resolution: the server owns max-wins.
    local value, failure = self.http.json("POST", self:base(), headers, {
        relative_path = key, char_offset = offset, source = "koreader", encoding = "UTF-8",
    })
    if not value then return nil, failure end
    return true
end

function Position:fetch(key)
    if key ~= "__connection_test__" and Paths.key(key) ~= key then return nil, "invalid_book_key" end
    local headers, reason = self:headers()
    if not headers then return nil, reason end
    local value, failure = self.http.json("GET", self:base()
        .. "?select=char_offset,source,encoding&relative_path=eq." .. require("socket.url").escape(key), headers)
    if not value then return nil, failure end
    if #value == 0 then return { absent = true } end
    local row = value[1]
    if #value ~= 1 or type(row) ~= "table" or not offset_valid(row.char_offset)
        or type(row.source) ~= "string" then return nil, "invalid_remote_position" end
    return row
end

function Position:verify()
    local ok, reason = self:push("__connection_test__", 1)
    if not ok then return nil, reason end
    ok, reason = self:push("__connection_test__", 0)
    if not ok then return nil, reason end
    local row, failure = self:fetch("__connection_test__")
    if not row then return nil, failure end
    if row.absent or row.char_offset < 1 then return nil, "server_max_wins_not_verified" end
    return true
end

function Position:loadQueue(path)
    local queue, reason = State.read(path)
    if not queue then
        if reason ~= "missing" then return nil, reason end
        queue = { version = 1, account = self.config.account_id, scope = Position.scope(self.config),
            root = self.config.library_root, jobs = {} }
    end
    if queue.version ~= 1 or type(queue.jobs) ~= "table" then return nil, "invalid_position_queue" end
    if queue.account ~= self.config.account_id or queue.scope ~= Position.scope(self.config) or queue.root ~= self.config.library_root then
        return nil, "position_queue_scope_changed_reset_required"
    end
    for key, job in pairs(queue.jobs) do
        if Paths.key(key) ~= key or type(job) ~= "table" or not offset_valid(job.offset)
            or type(job.hash) ~= "string" or not Paths.relative(job.relative) or Paths.key(job.relative) ~= key then
            return nil, "invalid_position_job"
        end
    end
    return queue
end

function Position:enqueue(path, relative, offset, expected_hash)
    if not offset_valid(offset) then return nil, "invalid_char_offset" end
    local physical, reason = Paths.resolve(self.config.library_root, relative)
    if not physical then return nil, reason end
    local key, key_reason = Paths.key(relative)
    if not key then return nil, key_reason end
    local hash, hash_reason = Hash.file(physical)
    if not hash then return nil, hash_reason end
    if expected_hash and hash ~= expected_hash then return nil, "position_body_changed" end
    local queue, failure = self:loadQueue(path)
    if not queue then return nil, failure end
    -- This is a durable unsent request, never a second current reading anchor.
    queue.jobs[key] = { relative = relative, offset = offset, hash = hash }
    return State.write(path, queue)
end

function Position:flush(path)
    if not self.config.position_verified then return nil, "position_connection_not_verified" end
    local queue, reason = self:loadQueue(path)
    if not queue then return nil, reason end
    local sent, deferred = 0, {}
    for key, job in pairs(queue.jobs) do
        local physical = Paths.resolve(self.config.library_root, job.relative)
        local current = physical and Hash.file(physical)
        if current ~= job.hash then
            deferred[#deferred + 1] = "position_body_changed_or_missing"
        else
            local ok, failure = self:push(key, job.offset)
            if ok then
                queue.jobs[key] = nil
                local saved, save_reason = State.write(path, queue)
                if not saved then return nil, save_reason end
                sent = sent + 1
            else deferred[#deferred + 1] = failure end
        end
    end
    return { sent = sent, deferred = deferred }
end

return Position
