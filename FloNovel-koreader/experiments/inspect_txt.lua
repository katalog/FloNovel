-- Characterize actual TXT parser output before choosing a position mapping.
require("setupkoenv")
local cre = require("libs/libkoreader-cre")
local json = require("json")
local root = "../../../../.work/results/position-probe"
local file = assert(io.open(root .. "/manifest.json", "rb"))
local manifest = json.decode(file:read("*a"))
file:close()
cre.initCache(root .. "/cache", 8 * 1024 * 1024)
cre.registerFont("fonts/freefont/FreeSans.ttf")
local output = { dom_version = cre.getLatestDomVersion(), documents = {} }

for _, fixture in ipairs(manifest.fixtures) do
    local doc = cre.newDocView(600, 800, 0)
    doc:setIntProperty("crengine.file.txt.preformatted", 1)
    doc:setStringProperty("font.face.default", "FreeSans")
    doc:setIntProperty("crengine.render.requested_dom_version", cre.getLatestDomVersion())
    local loaded = doc:loadDocument(root .. "/" .. fixture.name .. ".txt")
    local result = { name = fixture.name, loaded = loaded, nodes = {} }
    if loaded then
        doc:renderDocument()
        result.body = doc:getTextFromXPointer("/FictionBook/body")
        result.html = doc:getHTMLFromXPointer("/FictionBook/body")
        result.initial = doc:getXPointer()
        -- Missing nodes return nil. The cap also catches unexpected DOM growth.
        for index = 1, 100 do
            local xp = "/FictionBook/body/pre[" .. index .. "]/text()"
            local text = doc:getTextFromXPointer(xp)
            if text == nil then break end
            result.nodes[#result.nodes + 1] = { path = xp, text = text }
        end
        local target = "/FictionBook/body/pre[2]/text().8"
        if doc:isXPointerInDocument(target) then
            result.target = target
            doc:gotoXPointer(target)
            result.after_goto = doc:getXPointer()
            result.target_prefix = doc:getTextFromXPointers("/FictionBook/body/pre[2]/text().0", target)
        end
    end
    output.documents[#output.documents + 1] = result
    doc:close()
end
file = assert(io.open(root .. "/inspection.json", "wb"))
file:write(json.encode(output))
file:close()
print("INSPECTED_DOCUMENTS " .. #output.documents)
