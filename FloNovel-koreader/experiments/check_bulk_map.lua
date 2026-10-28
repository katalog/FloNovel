require("setupkoenv")
local cre,json=require("libs/libkoreader-cre"),require("json")
local Map=dofile("../../../../experiments/txt_position_map.lua")
local Bulk=dofile("../../../../experiments/bulk_position_map.lua")
local project="../../../.."
local function read(path)
    local f=assert(io.open(path,"rb"));local text=f:read("*a");f:close();return text
end
local positions=project.."/.work/results/position-probe"
local scale=project.."/.work/results/scale-probe"
local fixtures=json.decode(read(positions.."/manifest.json"))
local goldens=json.decode(read(scale.."/manifest.json"))
local output={tests={},scale={},scope="PC_optional_bulk_mapping_not_product_or_Kindle"}
cre.initCache(scale.."/cache",16*1024*1024,true,40)
cre.registerFont("fonts/freefont/FreeSans.ttf")
local function check(name,passed,detail)
    output.tests[#output.tests+1]={name=name,passed=not not passed,detail=detail}
end
local function open(path,size)
    local doc=cre.newDocView(600,800,1)
    doc:setIntProperty("crengine.file.txt.preformatted",1)
    doc:setIntProperty("crengine.render.requested_dom_version",cre.getLatestDomVersion())
    doc:setStringProperty("font.face.default","FreeSans");doc:setFontSize(size)
    assert(doc:loadDocument(path));doc:renderDocument();return doc
end
local function compare(doc,source,samples,label,options)
    local reference,reference_error=Map.build(doc,source,options)
    local bulk,bulk_error=Bulk.build(doc,source,options)
    check(label.."/same_support_or_rejection",(reference~=nil)==(bulk~=nil)
        and (reference~=nil or reference_error==bulk_error),bulk_error)
    if not reference or not bulk then return end
    check(label.."/same_length_and_nodes",reference.length==bulk.length
        and json.encode(reference.nodes)==json.encode(bulk.nodes))
    for _,sample in ipairs(samples) do
        local expected,reference_reason=reference:to_xpointer(sample.offset)
        local actual,bulk_reason=bulk:to_xpointer(sample.offset)
        local prefix,point
        if actual then prefix,point=actual:match("^(.*)%.(%d+)$") end
        local character=prefix and doc:getTextFromXPointers(actual,prefix.."."..(tonumber(point)+1))
        check(label.."/offset_"..sample.offset,actual==expected and bulk_reason==reference_reason
            and (actual==nil or (bulk:from_xpointer(actual)==sample.offset and character==sample.character)))
    end
end
for _,fixture in ipairs(fixtures.fixtures) do
    for _,size in ipairs({18,32}) do
        local doc=open(positions.."/"..fixture.name..".txt",size)
        for _,known in ipairs({false,true}) do
            compare(doc,fixture.text,fixture.samples,fixture.name.."/font_"..size.."/known_"..tostring(known),
                {known_line_changes=known})
        end
        doc:close()
    end
end
for _,fixture in ipairs(goldens.goldens) do
    local doc=open(scale.."/"..fixture.name,18)
    compare(doc,fixture.text,fixture.samples,"golden/"..fixture.name)
    doc:close()
end
for _,fixture in ipairs(goldens.large) do
    local doc=open(scale.."/"..fixture.name,20)
    local source=read(scale.."/"..fixture.name)
    collectgarbage("collect")
    local start=os.clock()
    local reference=assert(Map.build(doc,source))
    local reference_cpu=os.clock()-start
    local reference_nodes=reference.nodes
    reference=nil;collectgarbage("collect")
    local heap,start_bulk=collectgarbage("count"),os.clock()
    local bulk,reason,stats=Bulk.build(doc,source)
    local row={name=fixture.name,bytes=fixture.bytes,reference_cpu=reference_cpu,
        bulk_cpu=os.clock()-start_bulk,lua_heap_growth_kib=collectgarbage("count")-heap,stages=stats}
    check(fixture.name.."/large_map_exists",bulk~=nil,reason)
    if bulk then
        check(fixture.name.."/all_node_fields_equal",json.encode(reference_nodes)==json.encode(bulk.nodes))
        for _,sample in ipairs(fixture.samples) do
            local xp=bulk:to_xpointer(sample.offset)
            local prefix,point
            if xp then prefix,point=xp:match("^(.*)%.(%d+)$") end
            local text=prefix and doc:getTextFromXPointers(xp,prefix.."."..(tonumber(point)+1))
            check(fixture.name.."/offset_"..sample.offset,xp and bulk:from_xpointer(xp)==sample.offset and text==sample.character)
        end
        row.nodes=#bulk.nodes
    end
    output.scale[#output.scale+1]=row
    doc:close()
end
for _,xml in ipairs({"<body><p>x</p></body>","<body><pre a='1'>x</pre></body>",
    "<body><pre><b>x</b></pre></body>","<body><pre>&unknown;</pre></body>","<body><pre>x</body>"}) do
    check("unsupported_structure/"..xml,Bulk.extract(xml)==nil)
end
local literal=Bulk.extract("<body><pre>&amp;lt; &lt; &amp;#65; 가😀</pre></body>")
check("entities_decoded_once",literal and literal[1]=="&lt; < &#65; 가😀")
output.total,output.failed=#output.tests,0
for _,test in ipairs(output.tests) do if not test.passed then output.failed=output.failed+1 end end
output.passed=output.total-output.failed
local file=assert(io.open(project.."/.work/results/focused/bulk-results.json","wb"))
file:write(json.encode(output));file:close()
print(string.format("BULK_MAP_CHECKS total=%d passed=%d failed=%d",output.total,output.passed,output.failed))
os.exit(output.failed==0 and 0 or 1)
