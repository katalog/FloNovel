"""Exercise the emulator-only no-index overlay with actual ReaderUI and a 5% CPU quota."""
from pathlib import Path
import importlib.util,json,subprocess,os
p=Path(__file__).resolve().parents[1]
assert (p/'.work/runtime/lib/koreader/git-rev').read_text().strip() == 'v2026.07.1'
spec=importlib.util.spec_from_file_location('emu',p/'tools/run_emulator.py');emu=importlib.util.module_from_spec(spec);spec.loader.exec_module(emu)
h,library,bootstrap=emu.prepare(p,'no-index-smoke',True)
source=(library/'no-index-8MiB.txt').read_text()
lines=source.splitlines(keepends=True);pre=0;units=0;cases=[]
selected={1000,len(lines)//2,len(lines)-3}
for i,line in enumerate(lines):
 raw=line.rstrip('\r\n')
 if raw.strip(' \t'):
  pre+=1
  if i in selected:cases.append(dict(xp=f'/FictionBook/body/pre[{pre}]/text().5',offset=units+len(raw[:5].encode('utf-16-le'))//2))
 units+=len(line.encode('utf-16-le'))//2
(h/'expected.json').write_text(json.dumps(cases))
transport=bootstrap.read_text().rsplit('dofile("reader.lua")',1)[0]
prefix=(p/'tests/check_reader.lua').read_text().split('local ui = open("book.txt")',1)[0]
prefix=prefix.replace('local path = project .. "/flonovel.koplugin"','local path = home .. "/plugins/flonovel.koplugin"')
body='''
local ui=open("library/no-index-8MiB.txt")
local manager=require("ui/uimanager");manager:show(ui);manager:forceRePaint()
local plugin=assert(ui.flonovel);plugin.options.position=true
plugin.ensureMap=function() error("full mapping must not run during experimental position sharing") end
local probe=dofile(home.."/no_index_position.lua")
local json,clock=require("json"),require("socket").gettime
local f=assert(io.open(home.."/expected.json","rb"));local cases=json.decode(f:read("*a"));f:close()
local timings={}
for _,case in ipairs(cases) do
 ui:handleEvent(Event:new("GotoXPointer",case.xp))
 local started=clock()
 local capture=assert(probe.capture(ui))
 local value=assert(probe.locate(ui.document.file,capture))
 timings[#timings+1]={wall=clock()-started,offset=value.offset,expected=case.offset}
 check("real ReaderUI capture and no-index exact offset",value.offset==case.offset)
 local reverse=assert(probe.reverse(ui.document.file,case.offset))
 check("remote offset resolves same native XPointer",ui.document:getNormalizedXPointer(reverse.xp)==ui.document:getNormalizedXPointer(case.xp))
 check("remote target matches native text",ui.document:getTextFromXPointer(reverse.node_path)==reverse.text)
end
local original=plugin.runNetwork
plugin.runNetwork=function(_,kind,task,callback)
 local result,reason=task();assert(result,reason);callback(result,plugin.generation)
end
plugin:checkpointPosition(true,false)
check("position checkpoint never builds a map",plugin.map==nil)
local State=require("flonovel.state")
local config=assert(plugin:loadConnection())
local remote=assert(require("flonovel.reading_position").new(config):fetch("no-index-8mib.txt"))
check("queued position reaches local mock server",remote.char_offset==cases[#cases].offset)
plugin:fetchPosition()
check("remote fetch never builds a map",plugin.map==nil)
plugin.runNetwork=original
local heartbeats={};local target=clock()+.2
local function beat()
 heartbeats[#heartbeats+1]=math.max(0,clock()-target)
 if #heartbeats==15 then manager:quit();return end
 target=clock()+.1;manager:scheduleIn(.1,beat)
end
manager:scheduleIn(.2,beat)
manager:scheduleIn(.3,function()plugin:checkpointPosition(true,false)end)
manager:run()
local maximum=0;for _,n in ipairs(heartbeats)do maximum=math.max(maximum,n)end
check("event loop heartbeat continues during background conversion",maximum<1)
check("no-index loop kept full map absent",plugin.map==nil)
ui:onClose()
local result={total=total,passed=total-failed,failed=failed,skipped=0,timings=timings,max_heartbeat_lateness_seconds=maximum,scope="Actual ReaderUI no-index emulator overlay; synthetic8MiB, CPU5%, not Kindle"}
local out=assert(io.open(home.."/no-index-results.json","wb"));out:write(json.encode(result));out:close()
print("NO_INDEX_UI "..json.encode(result));os.exit(failed==0 and 0 or 1)
'''
script=h/'test.lua';script.write_text(transport+prefix+body)
runtime=p/'.work/runtime/lib/koreader'
cmd=['systemd-run','--user','--scope','--quiet','--property=CPUQuota=5%','--property=CPUQuotaPeriodSec=100ms','--',str(runtime/'luajit'),'-joff',str(script)]
r=subprocess.run(cmd,cwd=runtime,env=dict(os.environ,KO_HOME=str(h),FLONOVEL_PROJECT=str(p),SDL_VIDEODRIVER='dummy',SDL_AUDIODRIVER='dummy'),capture_output=True,text=True)
(h/'test-process.log').write_text(r.stdout+r.stderr)
for l in r.stdout.splitlines():
 if l.startswith(('NO_INDEX_UI','FAIL ')):print(l)
if r.returncode:print((r.stderr+r.stdout)[-2500:]);print(r.stderr[:1500])
raise SystemExit(r.returncode)
