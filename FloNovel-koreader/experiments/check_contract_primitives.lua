require("setupkoenv")
local json,sha,utf8=require("json"),require("ffi/sha2"),require("ffi/utf8proc")
local root="../../../../.work/results/finish"
local function read(name)
    local f=assert(io.open(root.."/"..name,"rb"));local t=f:read("*a");f:close();return t
end
local raw=read("contracts-input.json")
local input,oracle,python=json.decode(raw),json.decode(read("contracts-oracle.json")),json.decode(read("contracts-python.json"))
assert(oracle.manifest_sha256==sha.sha256(raw) and python.manifest_sha256==sha.sha256(raw))
local out={tests={},path_observations={},scope="offline_hash_PKCE_and_path_characterization_not_live_services"}
local function check(name,passed) out.tests[#out.tests+1]={name=name,passed=not not passed} end
for index,row in ipairs(oracle.files) do
    local file=assert(io.open(root.."/"..row.name,"rb"))
    local parts={}
    while true do
        local block=file:read(4*1024*1024)
        if not block then break end
        parts[#parts+1]=sha.hex_to_bin(sha.sha256(block))
    end
    file:close()
    local actual=sha.sha256(table.concat(parts))
    check(row.name.."/matches_Kotlin",actual==row.hash)
    check(row.name.."/matches_Python",actual==python.files[index].hash)
end
local challenge=sha.bin_to_base64(sha.hex_to_bin(sha.sha256(input.verifier))):gsub("%+","-"):gsub("/","_"):gsub("=+$","")
check("PKCE_matches_Kotlin",challenge==oracle.pkce)
check("PKCE_matches_Python",challenge==python.pkce)
check("PKCE_matches_RFC_vector",challenge=="E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
for _,row in ipairs(oracle.paths) do
    local unified=row.input:gsub("\\","/")
    local candidate=utf8.lowercase(utf8.normalize_NFC(unified),false)
    local folded=utf8.lowercase(unified)
    out.path_observations[#out.path_observations+1]={input=row.input,kotlin=row.key,
        NFC_then_simple_lower=candidate,simple_lower_matches=candidate==row.key,
        default_casefold=folded,default_casefold_matches=folded==row.key}
end
out.total,out.failed=#out.tests,0
for _,test in ipairs(out.tests) do if not test.passed then out.failed=out.failed+1 end end
out.passed=out.total-out.failed
local f=assert(io.open(root.."/contract-primitives-results.json","wb"));f:write(json.encode(out));f:close()
print(string.format("CONTRACT_PRIMITIVES total=%d passed=%d failed=%d paths_observed=%d",out.total,out.passed,out.failed,#out.path_observations))
os.exit(out.failed==0 and 0 or 1)
