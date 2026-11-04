"""Check the ephemeral marker ToC with real ReaderUI, synthetic input and CPU 5%."""
from pathlib import Path
import importlib.util
import json
import os
import shutil
import subprocess
import uuid

project = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('emulator', project/'tools/run_emulator.py')
emulator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(emulator)
home, library, bootstrap = emulator.prepare(project, 'native-toc-'+uuid.uuid4().hex, True)
shutil.copyfile(project/'flonovel.koplugin/main.lua', home/'plugins/flonovel.koplugin/main.lua')
expected = sum(line.startswith('##') for line in (library/'no-index-8MiB.txt').read_text().splitlines())
(home/'expected.json').write_text(json.dumps(expected))
prefix = (project/'tests/check_reader.lua').read_text().split('local ui = open("book.txt")', 1)[0]
prefix = prefix.replace('local path = project .. "/flonovel.koplugin"', 'local path = home .. "/plugins/flonovel.koplugin"')
body = '''
local ui=open("library/no-index-8MiB.txt")
local manager=require("ui/uimanager");manager:show(ui);manager:forceRePaint()
local plugin=assert(ui.flonovel);plugin.options.position=false
plugin.ensureMap=function() error("native ToC must not prepare the full source map") end
local clock,json=require("socket").gettime,require("json")
local expected_file=assert(io.open(home.."/expected.json","rb"))
local expected=json.decode(expected_file:read("*a"));expected_file:close()
local started=clock();local elapsed,beats,maximum,target=nil,0,0,clock()+.1
local reading_position=ui.rolling:getLastProgress()
local nonmodal=false
local function heartbeat()
 maximum=math.max(maximum,clock()-target);beats=beats+1
 if plugin.toc_provider and not elapsed then elapsed=clock()-started end
 if plugin.chapter_task then
  local top=manager._window_stack[#manager._window_stack]
  if top and top.widget==ui then nonmodal=true end
 end
 if elapsed and beats>=20 then manager:quit();return end
 if clock()-started>120 then manager:quit();return end
 target=clock()+.1;manager:scheduleIn(.1,heartbeat)
end
plugin:queueNativeToc();manager:scheduleIn(.1,heartbeat);manager:run()
check("background marker ToC completed",plugin.toc_provider~=nil)
check("reader event loop stays responsive during preparation",maximum<1)
check("chapter preparation leaves reader on top of input stack",nonmodal)
ui.toc:fillToc()
check("native ToC includes every synthetic marker",#ui.toc.toc==expected)
local current=ui.rolling:getLastProgress()
check("preparing ToC does not change reading position",current==reading_position)
local navigation={}
if #ui.toc.toc>1 then
 ui:handleEvent(Event:new("GotoXPointer",ui.toc.toc[1].xpointer))
 ui:handleEvent(Event:new("GotoNextChapter"))
 navigation={actual=ui.rolling.current_page,expected=ui.toc.toc[2].page,
 first_page=ui.toc.toc[1].page,visible=ui.document:getVisiblePageNumberCount()}
 check("KOReader next chapter uses marker ToC",ui.rolling.current_page==ui.toc.toc[2].page)
 ui:handleEvent(Event:new("GotoPrevChapter"))
 check("KOReader previous chapter uses marker ToC",ui.rolling.current_page==ui.toc.toc[1].page)
end
check("chapter actions never build a source map",plugin.map==nil)
plugin:clearNativeToc()
check("clearing bridge releases only its own provider",plugin.toc_provider==nil)
ui:onClose()
local result={total=total,passed=total-failed,failed=failed,skipped=0,
 preparation_wall_seconds=elapsed,max_heartbeat_lateness_seconds=maximum,markers=expected,navigation=navigation,
 scope="Real ReaderUI, synthetic8MiB, CPU5%; no live services or Kindle"}
local out=assert(io.open(home.."/native-toc-results.json","wb"));out:write(json.encode(result));out:close()
print("NATIVE_TOC_UI "..json.encode(result));os.exit(failed==0 and 0 or 1)
'''
script = home/'native-toc-test.lua'
script.write_text(prefix+body)
runtime = project/'.work/runtime/lib/koreader'
command = ['systemd-run', '--user', '--scope', '--quiet', '--property=CPUQuota=5%',
           '--property=CPUQuotaPeriodSec=100ms', '--', str(runtime/'luajit'), '-joff', str(script)]
result = subprocess.run(command, cwd=runtime, env=dict(os.environ, KO_HOME=str(home),
    FLONOVEL_PROJECT=str(project), SDL_VIDEODRIVER='dummy', SDL_AUDIODRIVER='dummy'),
    capture_output=True, text=True)
(home/'native-toc-process.log').write_text(result.stdout+result.stderr)
for line in result.stdout.splitlines():
    if line.startswith(('NATIVE_TOC_UI', 'FAIL ')):
        print(line)
print('Report:', home)
if result.returncode:
    print((result.stderr+result.stdout)[-2500:])
raise SystemExit(result.returncode)
