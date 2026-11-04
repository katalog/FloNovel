"""Exercise the emulator-only no-index overlay with actual ReaderUI and a 5% CPU quota."""
from pathlib import Path
import importlib.util,json,subprocess,os,uuid
p=Path(__file__).resolve().parents[1]
assert (p/'.work/runtime/lib/koreader/git-rev').read_text().strip() == 'v2026.07.1'
spec=importlib.util.spec_from_file_location('emu',p/'tools/run_emulator.py');emu=importlib.util.module_from_spec(spec);spec.loader.exec_module(emu)
h,library,bootstrap=emu.prepare(p,'fast-product-'+uuid.uuid4().hex,True)
# Restore the unmodified product implementation, then enable its explicit opt-in.
import shutil
shutil.copyfile(p/'flonovel.koplugin/main.lua',h/'plugins/flonovel.koplugin/main.lua')
configuration=h/'settings/flonovel/config.json'
config=json.loads(configuration.read_text());config['fast_position_enabled']=True
configuration.write_text(json.dumps(config))
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
blank_source='\r\n \t\r\n## Blank start\r\n' + '한'*600 + '😀 long paragraph\r\n\r\n' + '가'*2500 + 'q\r\n\r\n' + ''.join(
    f'Unique paragraph {i:04d}: 한글😀 reading position.\r\n\r\n' for i in range(100))
(library/'blank-start.txt').write_bytes(('\ufeff'+blank_source).encode('utf-8'))
(h/'blank-target.json').write_text(json.dumps(len(blank_source[:blank_source.index('Unique paragraph 0030')].encode('utf-16-le'))//2))
transport=bootstrap.read_text().rsplit('dofile("reader.lua")',1)[0]
prefix=(p/'tests/check_reader.lua').read_text().split('local ui = open("book.txt")',1)[0]
prefix=prefix.replace('local path = project .. "/flonovel.koplugin"','local path = home .. "/plugins/flonovel.koplugin"')
body='''
local ui=open("library/no-index-8MiB.txt")
local manager=require("ui/uimanager");manager:show(ui);manager:forceRePaint()
local plugin=assert(ui.flonovel);plugin.options.position=true
-- The marker-ToC worker has its own CPU-5% experiment. Isolate the position
-- worker here so its heartbeat threshold measures the feature named by this probe.
plugin.options.chapters=false;plugin:clearNativeToc()
plugin.ensureMap=function() error("full mapping must not run during experimental position sharing") end
local probe=require("flonovel.fast_position")
check("portable Unicode backend available",probe.supported)
local json,clock=require("json"),require("socket").gettime
local f=assert(io.open(home.."/expected.json","rb"));local cases=json.decode(f:read("*a"));f:close()
local timings={}
local saved_network=plugin.runNetwork
plugin.runNetwork=function(_,kind,task,callback)local result,reason=task();assert(result,reason);callback(result,plugin.generation)end
plugin:fetchPosition()
plugin.runNetwork=saved_network
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
ui=open("library/blank-start.txt")
plugin=assert(ui.flonovel);plugin.options.position=true
plugin.ensureMap=function() error("blank recovery must not build a full map") end
plugin.runNetwork=function(_,kind,task,callback)
 local value,reason=task();assert(value,reason);callback(value,plugin.generation)
end
config=assert(plugin:loadConnection())
local blank_xp="/FictionBook/body/empty-line[1].0"
ui:handleEvent(Event:new("GotoXPointer",blank_xp))
local blank_capture=assert(probe.capture(ui))
local blank_value=assert(probe.locate(ui.document.file,blank_capture))
check("leading blank has exact zero UTF16 offset",blank_value.offset==0)
local client=require("flonovel.reading_position").new(config)
local target_file=assert(io.open(home.."/blank-target.json","rb"))
local remote_target=json.decode(target_file:read("*a"));target_file:close()
assert(client:push("blank-start.txt",remote_target))
local confirmation
local show=manager.show
manager.show=function(self,widget,...)
 if widget.ok_callback then confirmation=widget;return end
 return show(self,widget,...)
end
plugin:fetchPosition()
manager.show=show
check("remote position prompts from a leading blank",confirmation~=nil)
if confirmation then confirmation.ok_callback() end
local moved=assert(probe.locate(ui.document.file,assert(probe.capture(ui))))
check("confirmed remote jump preserves exact UTF16 offset",moved.offset==remote_target)
ui:handleEvent(Event:new("GotoXPointer",blank_xp))
plugin:checkpointPosition(true,false)
local retained=assert(client:fetch("blank-start.txt"))
check("blank checkpoint uploads without lowering server maximum",retained.char_offset==remote_target)
check("blank recovery never builds full map",plugin.map==nil)
local synchronous=plugin.runNetwork
local handles={}
plugin.runNetwork=function(self,kind,task,callback,interactive,control)
 handles[#handles+1]={interactive=interactive,nonmodal=type(control)=="table"}
 return synchronous(self,kind,task,callback)
end
confirmation=nil
manager.show=function(self,widget,...)
 if widget.ok_callback then confirmation=widget;return end
 return show(self,widget,...)
end
plugin:fetchPosition(false)
manager.show=show
check("both automatic lookup stages are nonmodal",#handles==2 and handles[1].nonmodal and handles[2].nonmodal)
check("automatic check offers remote jump",confirmation~=nil and plugin.remote_lookup_completed)
local target_xp=assert(probe.reverse(ui.document.file,remote_target)).xp
ui:handleEvent(Event:new("GotoXPointer",target_xp))
local message
plugin.message=function(_,text)message=text end
handles={}
plugin:fetchPosition(true)
check("manual lookup is interactive in both stages",#handles==2 and handles[1].interactive and handles[2].interactive and not handles[1].nonmodal)
check("manual no-later-position result is explicit",message==require("flonovel.strings")("remote_current"))
ui:handleEvent(Event:new("GotoXPointer",blank_xp))
local calls=0
plugin.runNetwork=function(self,kind,task,callback)
 calls=calls+1
 local value,reason=task();assert(value,reason)
 if calls==2 then ui:handleEvent(Event:new("GotoXPointer",target_xp)) end
 callback(value,plugin.generation)
end
plugin:fetchPosition(false)
check("changed page defers and retries lookup instead of losing it",plugin.position_lookup_retry==true and not plugin.remote_lookup_completed)
ui:onClose()
local result={total=total,passed=total-failed,failed=failed,skipped=0,timings=timings,max_heartbeat_lateness_seconds=maximum,scope="Actual product fast-position module; synthetic8MiB, CPU5%, no live services or Kindle"}
local out=assert(io.open(home.."/no-index-results.json","wb"));out:write(json.encode(result));out:close()
print("FAST_PRODUCT_UI "..json.encode(result));os.exit(failed==0 and 0 or 1)
'''
script=h/'test.lua';script.write_text(transport+prefix+body)
runtime=p/'.work/runtime/lib/koreader'
cmd=['systemd-run','--user','--scope','--quiet','--property=CPUQuota=5%','--property=CPUQuotaPeriodSec=100ms','--',str(runtime/'luajit'),'-joff',str(script)]
r=subprocess.run(cmd,cwd=runtime,env=dict(os.environ,KO_HOME=str(h),FLONOVEL_PROJECT=str(p),SDL_VIDEODRIVER='dummy',SDL_AUDIODRIVER='dummy'),capture_output=True,text=True)
(h/'test-process.log').write_text(r.stdout+r.stderr)
for l in r.stdout.splitlines():
 if l.startswith(('FAST_PRODUCT_UI','FAIL ')):print(l)
print('Report:',h)
if r.returncode:print((r.stderr+r.stdout)[-2500:]);print(r.stderr[:1500])
raise SystemExit(r.returncode)
