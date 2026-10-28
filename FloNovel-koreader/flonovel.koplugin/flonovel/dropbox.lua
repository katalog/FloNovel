local json = require("json")
local lfs = require("libs/libkoreader-lfs")
local Paths = require("flonovel.paths")
local Hash = require("flonovel.content_hash")
local State = require("flonovel.state")
local HTTP = require("flonovel.http")
local Dropbox = {}
Dropbox.__index = Dropbox

local function valid_hash(value)
    return type(value) == "string" and #value == 64 and value:match("^[a-f0-9]+$")
end

function Dropbox.new(config, transport)
    return setmetatable({ config = config, http = transport or HTTP }, Dropbox)
end

function Dropbox.scope(config)
    return require("ffi/sha2").sha256(table.concat({ config.account_id or "", config.app_key or "",
        config.dev == true and "dev" or "production" }, "\0"))
end

function Dropbox:refresh()
    local encode = require("socket.url").escape
    if type(self.config.app_key) ~= "string" or type(self.config.refresh_token) ~= "string" then
        return nil, "dropbox_not_connected"
    end
    local response, reason = self.http.request("POST", "https://api.dropboxapi.com/oauth2/token",
        { ["Content-Type"] = "application/x-www-form-urlencoded" },
        "grant_type=refresh_token&client_id=" .. encode(self.config.app_key)
            .. "&refresh_token=" .. encode(self.config.refresh_token))
    if not response then return nil, reason end
    local ok, value = pcall(json.decode, response.body)
    if not ok or type(value.access_token) ~= "string" then return nil, "invalid_token_response" end
    self.token = value.access_token
    local account, failure = self:call("users/get_current_account", nil)
    if not account then return nil, failure end
    if account.account_id ~= self.config.account_id then self.token = nil; return nil, "account_changed" end
    return true
end

function Dropbox:call(endpoint, data)
    if not self.token then return nil, "dropbox_not_connected" end
    return self.http.json("POST", "https://api.dropboxapi.com/2/" .. endpoint,
        { Authorization = "Bearer " .. self.token, ["Content-Type"] = "application/json" }, data or json.util.null)
end

function Dropbox:list()
    local page, reason = self:call("files/list_folder", { path = "/books", recursive = true,
        include_deleted = false })
    if not page then return nil, reason end
    local files, keys, cursors = {}, {}, {}
    while true do
        if type(page.entries) ~= "table" then return nil, "invalid_listing" end
        for _, entry in ipairs(page.entries) do
            if entry[".tag"] == "file" then
                local relative = type(entry.path_display) == "string" and entry.path_display:sub(8)
                if type(entry.path_display) ~= "string" or entry.path_display:sub(1, 7):lower() ~= "/books/" then
                    return nil, "invalid_remote_root"
                end
                if relative:lower():match("%.txt$") and not relative:lower():match("^%.flonovel/") then
                    local path, bad = Paths.relative(relative)
                    if not path then return nil, bad end
                    local key, unsupported = Paths.key(path)
                    if not key then return nil, unsupported end
                    if keys[key] then return nil, "remote_normalization_collision" end
                    if type(entry.rev) ~= "string" or not valid_hash(entry.content_hash) then return nil, "invalid_metadata" end
                    keys[key], files[path] = true, { rev = entry.rev, hash = entry.content_hash }
                end
            end
        end
        if not page.has_more then break end
        if type(page.cursor) ~= "string" or cursors[page.cursor] then return nil, "invalid_pagination" end
        cursors[page.cursor] = true
        page, reason = self:call("files/list_folder/continue", { cursor = page.cursor })
        if not page then return nil, reason end
    end
    return files
end

function Dropbox:download(relative, metadata, temporary)
    local file = io.open(temporary, "wb")
    if not file then return nil, "download_temp_unwritable" end
    local count = 0
    local response, reason = self.http.request("POST", "https://content.dropboxapi.com/2/files/download",
        { Authorization = "Bearer " .. self.token, ["Dropbox-API-Arg"] = json.encode{ path = "rev:" .. metadata.rev } },
        nil, function(chunk)
            if chunk then
                count = count + #chunk
                if count > 16 * 1024 * 1024 then return nil, "book_too_large" end
                if not file:write(chunk) then return nil, "download_write_failed" end
            end
            return 1
        end)
    local flushed = file:flush()
    local synced = flushed and require("ffi/util").fsyncOpenedFile(file, true)
    local closed = file:close()
    if not response then return nil, reason end
    if not synced or not closed then return nil, "download_flush_failed" end
    local hash = Hash.file(temporary)
    if hash ~= metadata.hash then return nil, "download_hash_mismatch" end
    return true
end

function Dropbox:delete(relative, metadata)
    return self:call("files/delete_v2", { path = "/books/" .. relative, parent_rev = metadata.rev })
end

function Dropbox:secret(dev)
    local chunks = {}
    local response, reason = self.http.request("POST", "https://content.dropboxapi.com/2/files/download",
        { Authorization = "Bearer " .. self.token, ["Dropbox-API-Arg"] = json.encode{
            path = dev and "/.flonovel/secret-dev.json" or "/.flonovel/secret.json" } }, nil,
        function(chunk)
            if chunk then
                chunks[#chunks + 1] = chunk
                if #table.concat(chunks) > 4096 then return nil, "secret_file_too_large" end
            end
            return 1
        end)
    if not response then return nil, reason end
    local ok, value = pcall(json.decode, table.concat(chunks))
    if not ok or type(value.secret) ~= "string" or value.secret == "" then return nil, "invalid_shared_secret" end
    return value.secret
end

local function owned_directory(root, name)
    local path = root .. "/" .. name
    local mode = lfs.symlinkattributes(path, "mode")
    if not mode and not lfs.mkdir(path) then return nil, "internal_directory_unwritable" end
    if lfs.symlinkattributes(path, "mode") ~= "directory" then return nil, "internal_directory_linked" end
    return path
end

local function scan(root, relative, keys)
    local directory = relative == "" and root or root .. "/" .. relative
    for name in lfs.dir(directory) do
        if name ~= "." and name ~= ".." and name:lower() ~= ".flonovel" then
            local path = relative == "" and name or relative .. "/" .. name
            local mode = lfs.symlinkattributes(root .. "/" .. path, "mode")
            if mode == "link" then return nil, "linked_library_entry" end
            if mode == "directory" then
                local ok, reason = scan(root, path, keys)
                if not ok then return nil, reason end
            elseif mode == "file" and name:lower():match("%.txt$") then
                local key, reason = Paths.key(path)
                if not key then return nil, reason end
                if keys[key] and keys[key] ~= path then return nil, "local_normalization_collision" end
                keys[key] = path
            end
        end
    end
    return true
end

function Dropbox:sync(state_path, root, open_file, confirmation)
    local actual, reason = Paths.root(root)
    if actual ~= root then return nil, reason or "library_changed" end
    if type(self.config.account_id) ~= "string" then return nil, "missing_account_identity" end
    local state, failure = State.read(state_path)
    if not state then
        if failure ~= "missing" then return nil, failure end
        state = { version = 1, account = self.config.account_id, scope = Dropbox.scope(self.config), root = root, files = {} }
    end
    if state.version ~= 1 or type(state.files) ~= "table" then return nil, "invalid_sync_state" end
    if state.account ~= self.config.account_id or state.scope ~= Dropbox.scope(self.config) or state.root ~= root then
        return nil, "sync_scope_changed_reset_required"
    end
    local keys = {}
    local scanned, scan_reason = scan(root, "", keys)
    if not scanned then return nil, scan_reason end
    local remote, remote_reason = self:list()
    if not remote then return nil, remote_reason end
    local remote_count = 0
    for path in pairs(remote) do
        remote_count = remote_count + 1
        local key = Paths.key(path)
        if keys[key] and keys[key] ~= path then return nil, "cross_side_normalization_collision" end
    end
    local plan, deletions, tracked = {}, 0, 0
    local paths = {}
    for path in pairs(remote) do paths[path] = true end
    local base_keys = {}
    for path, base in pairs(state.files) do
        local key = Paths.key(path)
        if not key or type(base) ~= "table" or not valid_hash(base.hash)
            or type(base.rev) ~= "string" or (base.status ~= "SYNCED" and base.status ~= "DOWNLOADING") then
            return nil, "invalid_sync_base"
        end
        if base_keys[key] then return nil, "base_normalization_collision" end
        base_keys[key] = path
        paths[path], tracked = true, tracked + 1
    end
    local report = { downloaded = 0, deleted_remote = 0, recovered = 0, deferred = {} }
    local new_local, new_remote, missing_remote, missing_local = {}, {}, {}, {}
    local function group(groups, hash, path)
        groups[hash] = groups[hash] or {}
        groups[hash][#groups[hash] + 1] = path
    end
    for _, path in pairs(keys) do
        if not state.files[path] then
            local hash = Hash.file(root .. "/" .. path)
            if not hash then return nil, "hash_unreadable" end
            group(new_local, hash, path)
        end
    end
    for path, metadata in pairs(remote) do
        if not state.files[path] then group(new_remote, metadata.hash, path) end
    end
    for path, base in pairs(state.files) do
        if not remote[path] then group(missing_remote, base.hash, path) end
        local physical = Paths.resolve(root, path)
        if physical and not lfs.symlinkattributes(physical) then group(missing_local, base.hash, path) end
    end
    local moved = {}
    for hash, sources in pairs(missing_local) do
        if #sources == 1 and new_local[hash] and #new_local[hash] == 1 then
            moved[sources[1]] = "local_move_preserved_not_uploaded"
        end
    end
    for hash, sources in pairs(missing_remote) do
        if #sources == 1 and new_remote[hash] and #new_remote[hash] == 1 then
            moved[sources[1]], moved[new_remote[hash][1]] = "remote_move_deferred", "remote_move_deferred"
        end
    end
    for path in pairs(paths) do
        local physical, unsafe = Paths.resolve(root, path)
        local r, b = remote[path], state.files[path]
        if not physical and unsafe == "parent_missing" and r and not b then
            physical, unsafe = Paths.resolve(root, path, "plan")
        end
        local hash
        if physical and lfs.symlinkattributes(physical, "mode") == "file" then
            hash, failure = Hash.file(physical)
            if not hash then return nil, failure end
        elseif not physical then
            -- Missing ancestor directories are not individual file deletions.
            report.deferred[#report.deferred + 1] = { path = path, reason = unsafe }
        end
        if physical then
            local action
            if moved[path] then action = moved[path]
            elseif physical == open_file then action = "open_book"
            elseif b and b.status == "DOWNLOADING" then
                if r and hash == r.hash then action = "record"
                elseif hash and hash ~= b.previous_hash then action = "local_changes_preserved"
                elseif r then action = "download" else action = "interrupted_remote_missing" end
            elseif not b then
                if hash then action = r and hash == r.hash and "record" or "untracked_local_file"
                else action = r and "download" end
            elseif hash == b.hash then
                if not r then action = "recover" elseif r.hash ~= b.hash then action = "download" else action = "record" end
            elseif not hash then
                if not r then action = "forget" elseif r.hash == b.hash then action = "delete_remote" else action = "download" end
            else action = "local_changes_preserved" end
            if action == "delete_remote" or action == "recover" then deletions = deletions + 1 end
            plan[#plan + 1] = { path = path, physical = physical, remote = r, action = action, observed_hash = hash }
        end
    end
    if remote_count == 0 or deletions >= 20 or (deletions >= 5 and deletions >= tracked * 0.3) then
        local members = { self.config.account_id, root }
        for _, item in ipairs(plan) do
            members[#members + 1] = table.concat({ item.path, item.action or "", item.observed_hash or "",
                item.remote and item.remote.rev or "", item.remote and item.remote.hash or "" }, "\0")
        end
        table.sort(members)
        local signature = require("ffi/sha2").sha256(table.concat(members, "\1"))
        if confirmation ~= signature then
            return { requires_confirmation = true, signature = signature, deletion_count = deletions,
                reason = remote_count == 0 and "remote_books_empty_confirmation_required" or "mass_deletion_confirmation_required" }
        end
    end
    local internal, internal_reason = owned_directory(root, ".flonovel")
    if not internal then return nil, internal_reason end
    local transfers = owned_directory(internal, "transfers")
    local recovery = owned_directory(internal, "recovery")
    if not transfers or not recovery then return nil, "internal_directory_unavailable" end
    local function persist()
        return State.write(state_path, state)
    end
    for _, item in ipairs(plan) do
        local path, r, action = item.path, item.remote, item.action
        local ok, err = true, nil
        if action == "download" then
            state.files[path] = { rev = r.rev, hash = r.hash, status = "DOWNLOADING",
                previous_hash = item.observed_hash }
            ok, err = persist()
            if not ok then return nil, err end
            local temporary = transfers .. "/" .. require("ffi/sha2").sha256(path) .. ".part"
            if lfs.symlinkattributes(temporary, "mode") and lfs.symlinkattributes(temporary, "mode") ~= "file" then
                return nil, "download_temp_linked"
            end
            ok, err = self:download(path, r, temporary)
            if ok then
                local target, bad = Paths.resolve(root, path, true)
                if not target then return nil, bad end
                -- Refuse a concurrent PC/USB edit after planning. A prior interrupted
                -- transfer owns only its temp file, never an unrelated replacement.
                local current = lfs.symlinkattributes(target, "mode") and Hash.file(target) or nil
                if current ~= item.observed_hash then return nil, "local_changed_during_download" end
                if current then
                    local backup = recovery .. "/" .. require("ffi/sha2").sha256(path .. current)
                    if lfs.symlinkattributes(backup) then
                        if Hash.file(backup) ~= current then return nil, "recovery_collision" end
                    else
                        if not os.rename(target, backup) then return nil, "recovery_failed" end
                        if not require("ffi/util").fsyncDirectory(backup) then return nil, "recovery_flush_failed" end
                    end
                end
                if not os.rename(temporary, target) then return nil, "download_replace_failed" end
                if not require("ffi/util").fsyncDirectory(target) then return nil, "download_directory_flush_failed" end
                local attributes = lfs.attributes(target)
                state.files[path] = { rev = r.rev, hash = r.hash, status = "SYNCED",
                    local_size = attributes.size, local_mtime = attributes.modification }
                report.downloaded = report.downloaded + 1
            end
        elseif action == "delete_remote" then
            local checked = Paths.resolve(root, path)
            if not checked or lfs.symlinkattributes(checked) then ok, err = nil, "local_changed_before_delete"
            else ok, err = self:delete(path, r) end
            if ok then state.files[path] = nil; report.deleted_remote = report.deleted_remote + 1 end
        elseif action == "recover" then
            local backup = recovery .. "/" .. require("ffi/sha2").sha256(path .. item.observed_hash)
            local checked = Paths.resolve(root, path)
            if not checked or Hash.file(checked) ~= item.observed_hash then ok, err = nil, "local_changed_before_recovery"
            elseif lfs.symlinkattributes(backup) then ok, err = nil, "recovery_exists"
            else ok = os.rename(item.physical, backup); err = not ok and "recovery_failed" end
            if ok then
                if not require("ffi/util").fsyncDirectory(backup)
                    or not require("ffi/util").fsyncDirectory(item.physical:match("^(.*)/[^/]+$")) then
                    return nil, "recovery_flush_failed"
                end
                state.files[path] = nil; report.recovered = report.recovered + 1
            end
        elseif action == "record" then
            local attributes = lfs.attributes(item.physical)
            state.files[path] = { rev = r.rev, hash = r.hash, status = "SYNCED",
                local_size = attributes.size, local_mtime = attributes.modification }
        elseif action == "forget" then state.files[path] = nil
        elseif action then ok, err = nil, action end
        if not ok then report.deferred[#report.deferred + 1] = { path = path, reason = err } end
        local saved, save_reason = persist()
        if not saved then return nil, save_reason end
    end
    return report
end

return Dropbox
