local sha = require("ffi/sha2")
local Hash = {}
-- Full-book Lua SHA-256 stalls the reader on devices without a fast JIT.
-- Use the same bundled libcrypto as TLS when available; keep offline reading
-- independent of that library and preserve Dropbox's block-hash algorithm.
local loaded, native = pcall(function()
    local ffi = require("ffi")
    ffi.cdef[[unsigned char *SHA256(const unsigned char *data, size_t length, unsigned char *digest);]]
    local crypto = ffi.loadlib("crypto", "57")
    assert(crypto.SHA256)
    return function(text)
        local output = ffi.new("unsigned char[32]")
        if crypto.SHA256(text, #text, output) == nil then error("sha256_failed") end
        return ffi.string(output, 32)
    end
end)
Hash.backend = loaded and "native" or "lua"
local function binary_digest(text)
    if loaded then return native(text) end
    return sha.hex_to_bin(sha.sha256(text))
end
function Hash.text(text)
    local digests = {}
    for start = 1, #text, 4 * 1024 * 1024 do
        digests[#digests + 1] = binary_digest(text:sub(start, start + 4 * 1024 * 1024 - 1))
    end
    return sha.bin_to_hex(binary_digest(table.concat(digests)))
end
function Hash.file(path)
    local file = io.open(path, "rb")
    if not file then return nil, "hash_unreadable" end
    local digests = {}
    while true do
        local block = file:read(4 * 1024 * 1024)
        if not block then break end
        digests[#digests + 1] = binary_digest(block)
    end
    -- Lua's read returns nil on EOF and I/O error. Confirm the expected size so
    -- an interrupted read cannot establish a base for a truncated book.
    local ending = file:seek()
    local size = file:seek("end")
    file:close()
    if not ending or ending ~= size then return nil, "hash_incomplete_read" end
    return sha.bin_to_hex(binary_digest(table.concat(digests))), size
end
return Hash
