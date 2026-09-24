"""Genera los mp3 de PLEX PLAY con voces neuronales (edge-tts) a partir de manifest.json."""
import asyncio, json, os, sys, tempfile, edge_tts
OUT = "out"; os.makedirs(OUT, exist_ok=True)
man = json.load(open("audio-gen/manifest.json", encoding="utf8"))
sem = asyncio.Semaphore(4)
fails = []
async def one(e):
    dst = os.path.join(OUT, e["f"])
    if os.path.exists(dst): return
    async with sem:
        for attempt in range(4):
            try:
                chunks = []
                for i, p in enumerate(e["parts"]):
                    tmp = os.path.join(tempfile.gettempdir(), f"{e['f']}.{i}.mp3")
                    await edge_tts.Communicate(p["t"], p["v"], rate=p.get("r", "-5%")).save(tmp)
                    chunks.append(open(tmp, "rb").read()); os.remove(tmp)
                open(dst, "wb").write(b"".join(chunks)); return
            except Exception as ex:
                await asyncio.sleep(3 * (attempt + 1)); err = ex
        fails.append((e["f"], str(err)))
async def main():
    await asyncio.gather(*(one(e) for e in man))
    print("ok", len(man) - len(fails), "fallos", len(fails))
    for f in fails[:20]: print(f)
    if len(fails) > len(man) * 0.05: sys.exit(1)
asyncio.run(main())
