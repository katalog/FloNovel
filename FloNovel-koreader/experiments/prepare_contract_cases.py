"""Create public-domain synthetic protocol vectors, not account credentials."""
import base64
import hashlib
import json
from pathlib import Path
import unicodedata

root=Path(__file__).resolve().parents[1]/".work/results/finish"
root.mkdir(parents=True,exist_ok=True)
paths=["Folder/BOOK.TXT","Folder\\Nested\\BOOK.TXT",unicodedata.normalize("NFD","판타지/소설.TXT"),
       "소설/제１장.TXT","カナ/ｶﾅ.TXT","Folder/İ.TXT","Folder/ΟΣ.TXT","Folder/ΟΣΑ.TXT",
       "Folder/ẞß.TXT","Folder/É.TXT","Folder/😀.TXT","Folder/A + B & C # ?.TXT",
       "Folder/ΟΣ","Folder/ΟΣ .TXT"]
files=[];hashes=[]
block=4*1024*1024
for size in (0,1,block-1,block,block+1,2*block+17):
    name=f"hash_{size}.bin"
    raw=(b"0123456789abcdef"*((size+15)//16))[:size]
    (root/name).write_bytes(raw)
    digests=b"".join(hashlib.sha256(raw[i:i+block]).digest() for i in range(0,len(raw),block))
    files.append(name);hashes.append({"name":name,"hash":hashlib.sha256(digests).hexdigest()})
verifier="dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
manifest={"paths":paths,"files":files,"verifier":verifier}
raw=(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n").encode()
(root/"contracts-input.json").write_bytes(raw)
(root/"contracts-python.json").write_text(json.dumps({"files":hashes,
    "pkce":base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("="),
    "manifest_sha256":hashlib.sha256(raw).hexdigest()},indent=2)+"\n")
print("PREPARED_CONTRACTS",len(paths),len(files))
