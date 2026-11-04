local json = require("json")
local lfs = require("libs/libkoreader-lfs")
local util = require("ffi/util")
local State = {}

function State.read(path)
    local mode = lfs.symlinkattributes(path, "mode")
    if not mode then return nil, "missing" end
    if mode ~= "file" then return nil, "state_not_regular_file" end
    local file = io.open(path, "rb")
    if not file then return nil, "state_unreadable" end
    local data = file:read(8 * 1024 * 1024 + 1)
    file:close()
    if not data or #data > 8 * 1024 * 1024 then return nil, "state_size_invalid" end
    local ok, value = pcall(json.decode, data)
    if not ok or type(value) ~= "table" then return nil, "state_corrupt" end
    return value
end

function State.write(path, value)
    local ok, data = pcall(json.encode, value)
    if not ok then return nil, "state_encoding_failed" end
    local temp = path .. ".new"
    local mode = lfs.symlinkattributes(temp, "mode")
    if mode and mode ~= "file" then return nil, "state_temp_not_regular" end
    mode = lfs.symlinkattributes(path, "mode")
    if mode and mode ~= "file" then return nil, "state_not_regular_file" end
    local file = io.open(temp, "wb")
    if not file then return nil, "state_temp_unwritable" end
    local written = file:write(data)
    local flushed = file:flush()
    local synced = written and flushed and util.fsyncOpenedFile(file, true)
    local closed = file:close()
    if not synced or not closed then return nil, "state_flush_failed" end
    local verified = State.read(temp)
    if not verified then return nil, "state_verification_failed" end
    if not os.rename(temp, path) then return nil, "state_replace_failed" end
    if not util.fsyncDirectory(path) then return nil, "state_directory_flush_failed" end
    return true
end

function State.directory()
    local base = require("datastorage"):getSettingsDir()
    if lfs.symlinkattributes(base, "mode") == nil and not lfs.mkdir(base) then return nil, "settings_unwritable" end
    if lfs.symlinkattributes(base, "mode") ~= "directory" then return nil, "settings_not_directory" end
    local path = base .. "/flonovel"
    if not lfs.symlinkattributes(path) and not lfs.mkdir(path) then return nil, "state_directory_unwritable" end
    if lfs.symlinkattributes(path, "mode") ~= "directory" then return nil, "state_directory_linked" end
    return path
end

return State
