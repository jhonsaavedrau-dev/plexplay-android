"""Genera los mp3 de PLEX PLAY con voces neuronales (edge-tts) a partir de manifest.json."""
import asyncio, json, os, sys, tempfile, time, edge_tts
OUT = "out"; os.makedirs(OUT, exist_ok=True)
man = json.load(open("audio-gen/manifest.json", encoding="utf8"))
sem = asyncio.Semaphore(6)
fails = []; T0 = time.time(); LIMITE = 45 * 60; hechos = [0]; saltados = []
async def one(e):
    dst = os.path.join(OUT, e["f"])
    if os.path.exists(dst): return
    async with sem:
        if time.time() - T0 > LIMITE: saltados.append(e["f"]); return  # se completa en otra tanda
        for attempt in range(4):
            try:
                chunks = []
                for i, p in enumerate(e["parts"]):
                    tmp = os.path.join(tempfile.gettempdir(), f"{e['f']}.{i}.mp3")
                    await asyncio.wait_for(edge_tts.Communicate(p["t"], p["v"], rate=p.get("r", "-5%")).save(tmp), 60)
                    chunks.append(open(tmp, "rb").read()); os.remove(tmp)
                open(dst, "wb").write(b"".join(chunks))
                if e.get("q"):  # recodificar (voz: 32 kbps mono basta y ocupa la mitad)
                    tmp2 = dst + ".tmp.mp3"
                    proc = await asyncio.create_subprocess_exec("ffmpeg", "-y", "-loglevel", "error", "-i", dst, "-ac", "1", "-b:a", e["q"], tmp2)
                    await proc.wait()
                    if proc.returncode == 0 and os.path.getsize(tmp2) > 1000: os.replace(tmp2, dst)
                hechos[0] += 1
                if hechos[0] % 200 == 0: print("hechos", hechos[0], "de", len(man), int(time.time() - T0), "s", flush=True)
                return
            except Exception as ex:
                await asyncio.sleep(3 * (attempt + 1)); err = ex
        fails.append((e["f"], str(err)))
async def main():
    await asyncio.gather(*(one(e) for e in man))
    print("ok", hechos[0], "fallos", len(fails), "pendientes", len(saltados), flush=True)
    if saltados: json.dump(saltados, open(os.path.join(OUT, "_pendientes.json"), "w"))
    for f in fails[:20]: print(f)
    if len(fails) > len(man) * 0.05: sys.exit(1)
asyncio.run(main())
