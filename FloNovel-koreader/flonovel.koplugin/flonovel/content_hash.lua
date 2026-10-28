local sha = require("ffi/sha2")
local Hash = {}
function Hash.text(text)
    local digests = {}
    for start = 1, #text, 4 * 1024 * 1024 do
        digests[#digests + 1] = sha.hex_to_bin(sha.sha256(text:sub(start, start + 4 * 1024 * 1024 - 1)))
    end
    return sha.sha256(table.concat(digests))
end
function Hash.file(path)
    local file = io.open(path, "rb")
    if not file then return nil, "hash_unreadable" end
    local digests = {}
    while true do
        local block = file:read(4 * 1024 * 1024)
        if not block then break end
        digests[#digests + 1] = sha.hex_to_bin(sha.sha256(block))
    end
    -- Lua's read returns nil on EOF and I/O error. Confirm the expected size so
    -- an interrupted read cannot establish a base for a truncated book.
    local ending = file:seek()
    local size = file:seek("end")
    file:close()
    if not ending or ending ~= size then return nil, "hash_incomplete_read" end
    return sha.sha256(table.concat(digests)), size
end
return Hash
