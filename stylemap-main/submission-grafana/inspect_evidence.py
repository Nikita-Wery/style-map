"""Offline PNG and ISO-BMFF/MP4 structure checks; no video playback claim."""
import argparse
import hashlib
import json
import struct
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
EXPECTED = ["01-application-health.png", "02-red-latency.png", "03-jvm-use.png",
            "04-readiness-sla.png", "05-1-postgresql.png", "05-2-postgresql.png",
            "06-query-inspector.png"]


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024*1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atoms(stream, start, end):
    pos = start
    while pos < end:
        assert end-pos >= 8, ("truncated atom header", pos)
        stream.seek(pos)
        size, kind = struct.unpack(">I4s", stream.read(8))
        header = 8
        if size == 1:
            size = struct.unpack(">Q", stream.read(8))[0]
            header = 16
        if size == 0:
            size = end-pos
        assert header <= size <= end-pos, (kind, pos, size, end)
        yield kind.decode("ascii"), pos+header, pos+size
        pos += size
    assert pos == end


def read_payload(stream, atom):
    stream.seek(atom[1])
    return stream.read(atom[2]-atom[1])


def clock(payload):
    if payload[0] == 0:
        scale, duration = struct.unpack_from(">II", payload, 12)
    else:
        assert payload[0] == 1
        scale, duration = struct.unpack_from(">IQ", payload, 20)
    assert scale > 0
    return dict(timescale=scale, duration_units=duration, duration_seconds=duration/scale)


def inspect():
    images = []
    for name in EXPECTED:
        path = ROOT/"screenshots"/name
        with Image.open(path) as im:
            assert im.format == "PNG"
            width, height = im.size
            im.verify()
        with Image.open(path) as im:
            im.load()  # Decode pixels too, not only PNG headers.
        images.append(dict(path=str(path.relative_to(ROOT)), format="PNG", width=width,
                           height=height, bytes=path.stat().st_size, sha256=sha(path)))
    path = ROOT/"screencast/grafana.mp4"
    assert path.stat().st_size > 0
    with path.open("rb") as stream:
        top = list(atoms(stream, 0, path.stat().st_size))
        kinds = [a[0] for a in top]
        assert all(k in kinds for k in ("ftyp", "moov", "mdat"))
        ftyp = read_payload(stream, next(a for a in top if a[0] == "ftyp"))
        moov = next(a for a in top if a[0] == "moov")
        children = list(atoms(stream, moov[1], moov[2]))
        movie_clock = clock(read_payload(stream, next(a for a in children if a[0] == "mvhd")))
        assert movie_clock["duration_seconds"] > 0
        tracks = []
        for trak in (a for a in children if a[0] == "trak"):
            tc = list(atoms(stream, trak[1], trak[2]))
            tkhd = read_payload(stream, next(a for a in tc if a[0] == "tkhd"))
            mdia = next(a for a in tc if a[0] == "mdia")
            mc = list(atoms(stream, mdia[1], mdia[2]))
            hdlr = read_payload(stream, next(a for a in mc if a[0] == "hdlr"))
            info = dict(handler=hdlr[8:12].decode("ascii"),
                        **clock(read_payload(stream, next(a for a in mc if a[0] == "mdhd"))))
            width, height = struct.unpack_from(">II", tkhd, len(tkhd)-8)
            info.update(width=width/65536, height=height/65536)
            minf = next(a for a in mc if a[0] == "minf")
            stbl = next(a for a in atoms(stream, minf[1], minf[2]) if a[0] == "stbl")
            tables = list(atoms(stream, stbl[1], stbl[2]))
            stsd = read_payload(stream, next(a for a in tables if a[0] == "stsd"))
            info["codec_fourcc"] = stsd[12:16].decode("ascii")
            stsz = read_payload(stream, next(a for a in tables if a[0] == "stsz"))
            info["samples"] = struct.unpack_from(">I", stsz, 8)[0]
            assert info["samples"] > 0 and info["duration_seconds"] > 0
            if info["handler"] == "vide":
                info["average_fps"] = info["samples"]/info["duration_seconds"]
            tracks.append(info)
        assert any(t["handler"] == "vide" for t in tracks)
        video = dict(path=str(path.relative_to(ROOT)), bytes=path.stat().st_size,
                     sha256=sha(path), major_brand=ftyp[:4].decode("ascii"),
                     top_level_atoms=kinds, **movie_clock, tracks=tracks,
                     validation="container headers/atom boundaries and nonzero sample counts; not full codec decoding or playback")
    previous = json.loads((ROOT/"export-review.json").read_text())
    for d in previous["dashboards"]:
        assert sha(ROOT/d["export"]) == d["sha256"], ("changed export", d["export"])
    return dict(checked_at=datetime.now(timezone.utc).isoformat(), images=images, video=video,
                original_export_hashes_match=True, evidence_categories=6,
                screenshot_review="Visual inspection documented in Отчёт.md; no new runtime queries")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, help="Create new results file; never overwrite")
    args = parser.parse_args()
    result = inspect()
    if args.output:
        with args.output.open("x") as output:
            json.dump(result, output, ensure_ascii=False, indent=2)
            output.write("\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))
