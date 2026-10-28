-- Run from the official Linux KOReader runtime; never open user documents.
require("setupkoenv")

local cre = require("libs/libkoreader-cre")
local json = require("json")
local project = "../../../.."
local results_dir = project .. "/.work/results/engine-smoke"
local fixture = results_dir .. "/synthetic.txt"
local output = { runtime = "v2026.07.1", dom_version = cre.getLatestDomVersion(), tests = {}, observations = {} }

local function check(name, passed, detail)
    output.tests[#output.tests + 1] = { name = name, passed = not not passed, detail = detail }
end

cre.initCache(results_dir .. "/cache", 8 * 1024 * 1024)
cre.registerFont("fonts/freefont/FreeSans.ttf")
local has_font = false
for _, face in ipairs(cre.getFontFaces()) do
    if face == "FreeSans" then has_font = true end
end
check("registered_font_available", has_font)

for _, preformatted in ipairs({ 0, 1 }) do
    local doc = cre.newDocView(600, 800, 0)
    doc:setIntProperty("crengine.file.txt.preformatted", preformatted)
    doc:setStringProperty("font.face.default", "FreeSans")
    local loaded = doc:loadDocument(fixture)
    check("load_txt_mode_" .. preformatted, loaded)
    if loaded then
        doc:renderDocument()
        local xp = doc:getXPointer()
        check("valid_xpointer_mode_" .. preformatted, xp and doc:isXPointerInDocument(xp), xp)
        local body = doc:getTextFromXPointer("/FictionBook/body")
        check("body_text_mode_" .. preformatted, body and body:find("Alpha", 1, true), body)
        output.observations[#output.observations + 1] = {
            preformatted = preformatted, xpointer = xp, body = body, pages = doc:getPages(),
        }
    end
    doc:close()
end

output.total, output.failed = #output.tests, 0
for _, result in ipairs(output.tests) do
    if not result.passed then output.failed = output.failed + 1 end
end
output.passed = output.total - output.failed
local file = assert(io.open(results_dir .. "/results.json", "wb"))
file:write(json.encode(output))
file:close()
print(string.format("ENGINE_SMOKE total=%d passed=%d failed=%d", output.total, output.passed, output.failed))
os.exit(output.failed == 0 and 0 or 1)
