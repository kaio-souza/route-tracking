#!/usr/bin/env python3
"""
Extrai uma região retangular de um arquivo PMTiles v3 remoto (ex.: build diário do
Protomaps) usando HTTP Range, sem baixar o planeta inteiro. Só biblioteca padrão.

Uso:
    python3 tools/pmtiles_extract.py SAIDA.pmtiles --bbox LON_MIN,LAT_MIN,LON_MAX,LAT_MAX \
        [--maxzoom 15] [--source URL]

Exemplos (ver docs/offline-maps.md):
    python3 tools/pmtiles_extract.py mata-sul-recife.pmtiles --bbox=-35.95,-9.00,-34.80,-7.90
    python3 tools/pmtiles_extract.py pernambuco.pmtiles --bbox=-41.40,-9.50,-34.80,-7.25

Dados © OpenStreetMap contributors (ODbL), tiles Protomaps Basemap.
"""
import argparse
import bisect
import datetime
import gzip
import io
import json
import math
import struct
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

HEADER_LEN = 127
COMPRESSION_NONE, COMPRESSION_GZIP = 1, 2
USER_AGENT = "distrava-pmtiles-extract/1.0"


# ---------------------------------------------------------------- HTTP

def fetch(url, offset, length, retries=5):
    req = urllib.request.Request(url, headers={
        "Range": f"bytes={offset}-{offset + length - 1}",
        "User-Agent": USER_AGENT,
    })
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                data = r.read()
            if len(data) != length:
                raise IOError(f"esperado {length} bytes, recebido {len(data)}")
            return data
        except (urllib.error.URLError, IOError, TimeoutError) as e:
            if attempt == retries - 1:
                raise
            time.sleep(2 ** attempt)
            print(f"  nova tentativa ({e})", file=sys.stderr)


# ---------------------------------------------------------------- PMTiles

def read_varint(buf, pos):
    shift = result = 0
    while True:
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, pos
        shift += 7


def write_varint(out, n):
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def decompress(data, kind):
    if kind == COMPRESSION_NONE:
        return data
    if kind == COMPRESSION_GZIP:
        return gzip.decompress(data)
    raise ValueError(f"compressão interna não suportada: {kind}")


def parse_header(b):
    if b[:7] != b"PMTiles" or b[7] != 3:
        raise ValueError("não é PMTiles v3")
    q = struct.unpack_from("<11Q", b, 8)
    h = dict(zip([
        "root_offset", "root_length", "metadata_offset", "metadata_length",
        "leaf_offset", "leaf_length", "data_offset", "data_length",
        "addressed_tiles", "tile_entries", "tile_contents"], q))
    h["clustered"], h["internal_compression"], h["tile_compression"], h["tile_type"], \
        h["min_zoom"], h["max_zoom"] = struct.unpack_from("<6B", b, 96)
    return h


def deserialize_directory(buf):
    n, pos = read_varint(buf, 0)
    ids, runs, lens, offs = [0] * n, [0] * n, [0] * n, [0] * n
    last = 0
    for i in range(n):
        v, pos = read_varint(buf, pos)
        last += v
        ids[i] = last
    for i in range(n):
        runs[i], pos = read_varint(buf, pos)
    for i in range(n):
        lens[i], pos = read_varint(buf, pos)
    for i in range(n):
        v, pos = read_varint(buf, pos)
        offs[i] = offs[i - 1] + lens[i - 1] if (v == 0 and i > 0) else v - 1
    return list(zip(ids, offs, lens, runs))


def serialize_directory(entries):
    out = bytearray()
    write_varint(out, len(entries))
    last = 0
    for tid, _, _, _ in entries:
        write_varint(out, tid - last)
        last = tid
    for e in entries:
        write_varint(out, e[3])
    for e in entries:
        write_varint(out, e[2])
    for i, e in enumerate(entries):
        if i > 0 and e[1] == entries[i - 1][1] + entries[i - 1][2]:
            write_varint(out, 0)
        else:
            write_varint(out, e[1] + 1)
    return gzip.compress(bytes(out), mtime=0)


def zxy_to_tileid(z, x, y):
    acc = ((1 << (2 * z)) - 1) // 3
    n = 1 << z
    d = 0
    s = n >> 1
    while s > 0:
        rx = 1 if x & s else 0
        ry = 1 if y & s else 0
        d += s * s * ((3 * rx) ^ ry)
        if ry == 0:
            if rx == 1:
                x = n - 1 - x
                y = n - 1 - y
            x, y = y, x
        s >>= 1
    return acc + d


def lonlat_to_tile(lon, lat, z):
    n = 1 << z
    x = int((lon + 180.0) / 360.0 * n)
    lat_r = math.radians(max(min(lat, 85.0511), -85.0511))
    y = int((1.0 - math.asinh(math.tan(lat_r)) / math.pi) / 2.0 * n)
    return min(max(x, 0), n - 1), min(max(y, 0), n - 1)


# ---------------------------------------------------------------- extração

def wanted_ids(bbox, min_zoom, max_zoom):
    lon0, lat0, lon1, lat1 = bbox
    ids = []
    for z in range(min_zoom, max_zoom + 1):
        x0, y0 = lonlat_to_tile(lon0, lat1, z)  # canto noroeste
        x1, y1 = lonlat_to_tile(lon1, lat0, z)  # canto sudeste
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                ids.append(zxy_to_tileid(z, x, y))
    ids.sort()
    return ids


def any_in_range(sorted_ids, lo, hi):
    """Existe id em [lo, hi)?"""
    i = bisect.bisect_left(sorted_ids, lo)
    return i < len(sorted_ids) and sorted_ids[i] < hi


def collect_entries(url, header, root, want):
    """Percorre diretórios e devolve [(tile_id, offset, length)] para os tiles desejados."""
    found = []
    pending = [root]
    leaf_fetches = 0
    while pending:
        next_pending = []
        leaf_jobs = []
        for entries in pending:
            for i, (tid, off, ln, run) in enumerate(entries):
                end = entries[i + 1][0] if i + 1 < len(entries) else float("inf")
                if run == 0:
                    if any_in_range(want, tid, end):
                        leaf_jobs.append((header["leaf_offset"] + off, ln))
                else:
                    lo = bisect.bisect_left(want, tid)
                    hi = bisect.bisect_left(want, tid + run)
                    for t in want[lo:hi]:
                        found.append((t, off, ln))
        if leaf_jobs:
            leaf_fetches += len(leaf_jobs)
            with ThreadPoolExecutor(8) as ex:
                for raw in ex.map(lambda j: fetch(url, *j), leaf_jobs):
                    next_pending.append(deserialize_directory(decompress(raw, header["internal_compression"])))
        pending = next_pending
    print(f"  diretórios-folha lidos: {leaf_fetches}")
    found.sort()
    return found


def plan_ranges(offsets, max_gap=256 * 1024, max_size=16 * 1024 * 1024):
    """Agrupa (offset, length) próximos em poucas requisições grandes."""
    groups = []
    for off, ln in offsets:
        if groups:
            g = groups[-1]
            g_end = g[0] + g[1]
            if off - g_end <= max_gap and (off + ln - g[0]) <= max_size:
                g[1] = max(g_end, off + ln) - g[0]
                g[2].append((off, ln))
                continue
        groups.append([off, ln, [(off, ln)]])
    return groups


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("output")
    ap.add_argument("--bbox", required=True, help="lon_min,lat_min,lon_max,lat_max")
    ap.add_argument("--maxzoom", type=int, default=15)
    ap.add_argument("--source", help="URL do PMTiles de origem (padrão: build do Protomaps de ontem)")
    args = ap.parse_args()

    bbox = [float(v) for v in args.bbox.split(",")]
    if len(bbox) != 4 or bbox[0] >= bbox[2] or bbox[1] >= bbox[3]:
        sys.exit("bbox inválido")

    url = args.source
    if not url:
        day = datetime.date.today() - datetime.timedelta(days=1)
        url = f"https://build.protomaps.com/{day:%Y%m%d}.pmtiles"
    print(f"origem: {url}")

    head = fetch(url, 0, 16384)
    header = parse_header(head)
    if header["internal_compression"] != COMPRESSION_GZIP:
        print("aviso: compressão interna inesperada", file=sys.stderr)
    root = deserialize_directory(decompress(
        head[header["root_offset"]:header["root_offset"] + header["root_length"]],
        header["internal_compression"]))
    metadata = decompress(fetch(url, header["metadata_offset"], header["metadata_length"]),
                          header["internal_compression"])

    max_zoom = min(args.maxzoom, header["max_zoom"])
    want = wanted_ids(bbox, header["min_zoom"], max_zoom)
    print(f"tiles no recorte (z{header['min_zoom']}-{max_zoom}): {len(want)}")

    found = collect_entries(url, header, root, want)
    print(f"tiles existentes: {len(found)}")

    uniq = sorted({(off, ln) for _, off, ln in found})
    total = sum(ln for _, ln in uniq)
    groups = plan_ranges(uniq)
    print(f"conteúdos únicos: {len(uniq)} ({total / 1e6:.1f} MB) em {len(groups)} requisições")

    # Baixa os grupos e grava o conteúdo de cada tile num arquivo temporário em ordem de tile_id.
    blobs = {}
    done = [0]

    def get_group(g):
        start, length, members = g
        data = fetch(url, header["data_offset"] + start, length)
        part = {}
        for off, ln in members:
            part[(off, ln)] = data[off - start:off - start + ln]
        done[0] += length
        print(f"\r  baixado {done[0] / 1e6:.1f} MB", end="", flush=True)
        return part

    with ThreadPoolExecutor(8) as ex:
        for part in ex.map(get_group, groups):
            blobs.update(part)
    print()

    # Monta a saída: dados em ordem de tile_id, conteúdo repetido reaproveitado (run-length).
    tile_data = io.BytesIO()
    new_offset = {}
    entries = []
    for tid, off, ln in found:
        key = (off, ln)
        if key not in new_offset:
            new_offset[key] = tile_data.tell()
            tile_data.write(blobs[key])
        noff = new_offset[key]
        if entries and entries[-1][0] + entries[-1][3] == tid and entries[-1][1] == noff:
            e = entries[-1]
            entries[-1] = (e[0], e[1], e[2], e[3] + 1)
        else:
            entries.append((tid, noff, ln, 1))

    # Diretórios: tudo na raiz se couber, senão folhas.
    root_bytes = serialize_directory(entries)
    leaf_bytes = b""
    if HEADER_LEN + len(root_bytes) > 16384:
        leaf_size = 4096
        while True:
            leaves = bytearray()
            root_entries = []
            for i in range(0, len(entries), leaf_size):
                chunk = entries[i:i + leaf_size]
                ser = serialize_directory(chunk)
                root_entries.append((chunk[0][0], len(leaves), len(ser), 0))
                leaves += ser
            root_bytes = serialize_directory(root_entries)
            if HEADER_LEN + len(root_bytes) <= 16384:
                leaf_bytes = bytes(leaves)
                break
            leaf_size *= 2

    meta_bytes = gzip.compress(metadata, mtime=0)
    data_bytes = tile_data.getvalue()

    root_off = HEADER_LEN
    meta_off = root_off + len(root_bytes)
    leaf_off = meta_off + len(meta_bytes)
    data_off = leaf_off + len(leaf_bytes)

    lon0, lat0, lon1, lat1 = bbox
    out_header = bytearray(b"PMTiles\x03")
    out_header += struct.pack(
        "<11Q", root_off, len(root_bytes), meta_off, len(meta_bytes), leaf_off, len(leaf_bytes),
        data_off, len(data_bytes), len(found), len(entries), len(uniq))
    out_header += struct.pack("<6B", 1, COMPRESSION_GZIP, header["tile_compression"], header["tile_type"],
                              header["min_zoom"], max_zoom)
    e7 = lambda v: int(round(v * 1e7))
    out_header += struct.pack("<4i", e7(lon0), e7(lat0), e7(lon1), e7(lat1))
    out_header += struct.pack("<B2i", min(max_zoom, 12), e7((lon0 + lon1) / 2), e7((lat0 + lat1) / 2))
    assert len(out_header) == HEADER_LEN

    with open(args.output, "wb") as f:
        f.write(out_header)
        f.write(root_bytes)
        f.write(meta_bytes)
        f.write(leaf_bytes)
        f.write(data_bytes)
    size = HEADER_LEN + len(root_bytes) + len(meta_bytes) + len(leaf_bytes) + len(data_bytes)
    print(f"gravado {args.output}: {size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
