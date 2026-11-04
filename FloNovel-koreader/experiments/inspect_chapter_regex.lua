-- Diagnostic comparison: mismatches are observations, not a parity pass claim.
require("setupkoenv")
local cre,json,sha=require("libs/libkoreader-cre"),require("json"),require("ffi/sha2")
local Map=dofile("../../../../experiments/txt_position_map.lua")
local root="../../../../.work/results/focused"
local function read(name)
    local f=assert(io.open(root.."/"..name,"rb"));local t=f:read("*a");f:close();return t
end
local raw=read("chapter-cases.json")
local cases=json.decode(raw).cases
local oracle=json.decode(read("chapter-oracle.json"))
assert(oracle.manifest_sha256==sha.sha256(raw),"Stale Kotlin chapter oracle")
cre.initCache(root.."/cache",8*1024*1024,true,40)
cre.registerFont("fonts/freefont/FreeSans.ttf")
local output={cases={},scope="native_regex_dialect_and_side_effect_characterization_not_compatibility_claim"}
for index,case in ipairs(cases) do
    local doc=cre.newDocView(600,800,1)
    doc:setIntProperty("crengine.file.txt.preformatted",1)
    doc:setIntProperty("crengine.render.requested_dom_version",cre.getLatestDomVersion())
    doc:setStringProperty("font.face.default","FreeSans")
    assert(doc:loadDocument(root.."/chapter_"..case.name..".txt"));doc:renderDocument()
    local map=assert(Map.build(doc,case.text))
    local row={name=case.name,pattern=case.pattern,kotlin=oracle.cases[index],
        native_compile_error=doc:checkRegex(case.pattern),native_matches={},filtered_line_offsets={}}
    assert(row.kotlin.name==case.name)
    if row.native_compile_error==0 then
        local matches=doc:findAllText(case.pattern,false,true,case.max_hits or 100,true,0,1) or {}
        row.native_search_error=doc:getAndClearRegexSearchError()
        for _,match in ipairs(matches) do
            local start=map:from_xpointer(match.start)
            local ending=map:from_xpointer(match["end"])
            row.native_matches[#row.native_matches+1]={start=start,ending=ending,text=match.matched_text}
            for _,node in ipairs(map.nodes) do
                if start==node.start and ending==node.start+node.length and (case.preset or node.length<=60) then
                    row.filtered_line_offsets[#row.filtered_line_offsets+1]=node.start
                end
            end
        end
        doc:clearSelection()
    end
    row.native_match_count=#row.native_matches
    local expected={}
    for _,chapter in ipairs(row.kotlin.chapters) do expected[#expected+1]=chapter.offset end
    row.offsets_equal_after_boundary_and_length_filter=#expected==#row.filtered_line_offsets
    for i,value in ipairs(expected) do
        if row.filtered_line_offsets[i]~=value then row.offsets_equal_after_boundary_and_length_filter=false end
    end
    output.cases[#output.cases+1]=row
    doc:close()
end
output.case_count=#output.cases;output.equal_offset_cases=0
for _,row in ipairs(output.cases) do if row.offsets_equal_after_boundary_and_length_filter then
    output.equal_offset_cases=output.equal_offset_cases+1 end end
local f=assert(io.open(root.."/chapter-regex-results.json","wb"));f:write(json.encode(output));f:close()
print(string.format("CHAPTER_REGEX_OBSERVATIONS cases=%d equal_offset_cases=%d",output.case_count,output.equal_offset_cases))
