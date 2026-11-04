local Dispatcher = require("dispatcher")
local WidgetContainer = require("ui/widget/container/widgetcontainer")

local Probe = WidgetContainer:extend{ is_doc_only = true }

function Probe:init()
    Dispatcher:registerAction("flonovel_probe_position", {
        category = "none", event = "FloNovelProbePosition", title = "FloNovel probe position", reader = true,
    })
    Dispatcher:registerAction("flonovel_probe_next", {
        category = "none", event = "FloNovelProbeNext", title = "FloNovel probe next subdivision", reader = true,
    })
    Dispatcher:registerAction("flonovel_probe_previous", {
        category = "none", event = "FloNovelProbePrevious", title = "FloNovel probe previous subdivision", reader = true,
    })
    self.ui.menu:registerToMainMenu(self)
end

function Probe:navigateSubdivision(forward)
    if not self.probe_map or not self.probe_points then self.navigation_error = "probe_not_configured"; return true end
    local current = self.probe_map:from_xpointer(self.ui.rolling:getLastProgress())
    if current == nil then self.navigation_error = "unmapped_current_anchor"; return true end
    local target
    if forward then
        for _, point in ipairs(self.probe_points) do if point > current then target = point; break end end
    else
        for index = #self.probe_points, 1, -1 do
            if self.probe_points[index] < current then target = self.probe_points[index]; break end
        end
    end
    if not target then self.navigation_error = "no_subdivision_in_direction"; return true end
    local xp, err = self.probe_map:to_exact_boundary(target)
    if not xp then self.navigation_error = err; return true end
    self.navigation_error = nil
    self.ui:handleEvent(require("ui/event"):new("GotoXPointer", xp))
    return true
end

function Probe:onFloNovelProbeNext() return self:navigateSubdivision(true) end
function Probe:onFloNovelProbePrevious() return self:navigateSubdivision(false) end

function Probe:addToMainMenu(items)
    items.flonovel_probe = {
        text = "FloNovel probe position",
        sorting_hint = "more_tools",
        callback = function() self:onFloNovelProbePosition() end,
    }
end

function Probe:onReaderReady()
    self.reader_ready = true
end

function Probe:onFloNovelProbePosition()
    -- Use the reader's own anchor, never a second synchronization position.
    self.last_observation = self.ui.rolling:getLastProgress()
    return true
end

return Probe
