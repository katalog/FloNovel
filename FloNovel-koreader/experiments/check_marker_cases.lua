require("setupkoenv")
local json,sha=require("json"),require("ffi/sha2")
local Probe=dofile("../../../../experiments/chapter_probe.lua")
local Map=dofile("../../../../experiments/txt_position_map.lua")
local root="../../../../.work/results/focused"
local function read(name)
    local f=assert(io.open(root.."/"..name,"rb"));local t=f:read("*a");f:close();return t
end
local raw=read("marker-cases.json")
local cases,oracle=json.decode(raw).cases,json.decode(read("marker-oracle.json"))
assert(sha.sha256(raw)==oracle.manifest_sha256,"Stale marker oracle")
local output={tests={},scope="trusted_hash_marker_and_breakpoints_against_Desktop_Kotlin_not_product_or_Kindle"}
local function check(name,passed)
    output.tests[#output.tests+1]={name=name,passed=not not passed}
end
for index,case in ipairs(cases) do
    local expected=oracle.cases[index]
    assert(expected.name==case.name)
    check(case.name.."/utf16_length",Map.utf16_length(case.text)==expected.length)
    for _,divisions in ipairs({1,2,4,8,16}) do
        local points,chapters=Probe.breakpoints(case.text,divisions)
        local same=#chapters==#expected.chapters
        for i,chapter in ipairs(chapters) do
            same=same and expected.chapters[i]~=nil and chapter.title==expected.chapters[i].title
                and chapter.offset==expected.chapters[i].offset
        end
        check(case.name.."/chapters/divisions_"..divisions,same)
        local wanted=expected.points[tostring(divisions)]
        same=#points==#wanted
        for i,value in ipairs(points) do same=same and value==wanted[i] end
        check(case.name.."/points/divisions_"..divisions,same)
    end
end
output.total,output.failed=#output.tests,0
for _,test in ipairs(output.tests) do if not test.passed then output.failed=output.failed+1 end end
output.passed=output.total-output.failed
local f=assert(io.open(root.."/marker-results.json","wb"));f:write(json.encode(output));f:close()
print(string.format("MARKER_CHECKS total=%d passed=%d failed=%d",output.total,output.passed,output.failed))
os.exit(output.failed==0 and 0 or 1)
