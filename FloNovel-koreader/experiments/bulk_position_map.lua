-- Experimental fast path for one exact crengine TXT serialization grammar.
-- Keep the reference mapper and its rejection/offset rules unchanged.
local Map = dofile("../../../../experiments/txt_position_map.lua")
local Bulk = {}
local entities = {lt="<",gt=">",amp="&",quot='"',apos="'"}
local function decode(text)
    local invalid = false
    local decoded = text:gsub("&([^;]+);", function(entity)
        if entities[entity] then return entities[entity] end
        -- The inspected TXT exporter uses XML's five named entities only.
        invalid = true
        return "&" .. entity .. ";"
    end)
    if invalid then return nil, "unsupported_serialized_entity" end
    return decoded
end

function Bulk.extract(xml)
    if type(xml) ~= "string" or xml:sub(1,6) ~= "<body>" or xml:sub(-7) ~= "</body>" then
        return nil, "unsupported_serialized_body"
    end
    local nodes, cursor, limit = {}, 7, #xml - 7
    while cursor <= limit do
        if xml:sub(cursor,cursor+4) == "<pre>" then
            local ending = xml:find("</pre>",cursor+5,true)
            if not ending or ending > limit then return nil,"unclosed_serialized_pre" end
            local encoded = xml:sub(cursor+5,ending-1)
            if encoded:find("<",1,true) then return nil,"unsupported_nested_dom" end
            local text, reason = decode(encoded)
            if not text then return nil,reason end
            nodes[#nodes+1] = text
            cursor = ending+6
        elseif xml:sub(cursor,cursor+12) == "<empty-line/>" then
            cursor = cursor+13
        else
            return nil,"unsupported_serialized_element"
        end
    end
    if cursor ~= limit+1 then return nil,"invalid_serialized_extent" end
    return nodes
end

function Bulk.build(doc, source, options)
    local started = os.clock()
    local xml = doc:getHTMLFromXPointer("/FictionBook/body")
    local exported = os.clock()
    local nodes, reason = Bulk.extract(xml)
    local parsed = os.clock()
    local stats = {export_cpu=exported-started,parse_cpu=parsed-exported,
        serialized_bytes=type(xml)=="string" and #xml or 0}
    if not nodes then return nil,reason,stats end
    -- Only Map.build's sequential node reads use this facade. All subsequent
    -- XPointer validation and navigation still use the real engine document.
    local facade = {getTextFromXPointer=function(_,path)
        local index=path:match("^/FictionBook/body/pre%[(%d+)%]/text%(%)$")
        assert(index,"Unexpected reference-mapper node request")
        return nodes[tonumber(index)]
    end}
    local map, failure = Map.build(facade,source,options)
    stats.validation_cpu=os.clock()-parsed
    if map then map.doc=doc end
    return map,failure,stats
end

return Bulk
