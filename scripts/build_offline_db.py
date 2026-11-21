#!/usr/bin/env python3
"""
Build PocketDeck's bundled read-only offline database under:
  app/src/main/assets/offline_db/boxart/<safe_system>.txt
  app/src/main/assets/offline_db/meta/<safe_system>.tsv

Plain UTF-8 files (APK packaging compresses assets natively with DEFLATE).

Sources:
  1. libretro/libretro-database (No-Intro / Redump / FBNeo / MAME / TOSEC DATs + metadat/*)
  2. libretro-thumbnails/<repo> Named_Boxarts/*.png (fetched via blobless git ls-tree, no API rate limit)

Usage:
  python3 scripts/build_offline_db.py --db-repo /tmp/pocketdeck_db_build/libretro-database
"""

import argparse
import concurrent.futures
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLATFORM_CATALOG = ROOT / "app/src/main/java/com/pocketdeck/launcher/data/PlatformCatalog.kt"
OUT_ROOT = ROOT / "app/src/main/assets/offline_db"
OUT_BOXART = OUT_ROOT / "boxart"
OUT_META = OUT_ROOT / "meta"

SAFE_FILE_RE = re.compile(r"[^a-zA-Z0-9._-]")
PLATFORM_SYS_RE = re.compile(r'p\([^)]*?,\s*"([^"]+)"\s*,\s*(?:"[^"]*"|null)\s*\)')

GAME_START_RE = re.compile(r'^\s*game\s*\(')
NAME_RE = re.compile(r'\b(?:name|comment)\s+"((?:\\.|[^"\\])*)"')
DEV_RE = re.compile(r'\bdeveloper\s+"((?:\\.|[^"\\])*)"')
PUB_RE = re.compile(r'\bpublisher\s+"((?:\\.|[^"\\])*)"')
GENRE_RE = re.compile(r'\bgenre\s+"((?:\\.|[^"\\])*)"')
YEAR_RE = re.compile(r'\breleaseyear\s+"?(\d{4})"?')
MONTH_RE = re.compile(r'\breleasemonth\s+"?(\d{1,2})"?')
CRC_RE = re.compile(r'\bcrc\s+([0-9A-Fa-f]{8})\b')
ROM_LINE_RE = re.compile(r'^\s*rom\s*\(')


def sanitize_file_name(name: str) -> str:
    return SAFE_FILE_RE.sub("_", name)


def unescape_dat(s: str) -> str:
    return (
        s.replace('\\"', '"')
        .replace("\\\\", "\\")
        .replace("\t", " ")
        .replace("\r", "")
        .replace("\n", " ")
        .strip()
    )


def load_systems_from_catalog() -> list[str]:
    text = PLATFORM_CATALOG.read_text(encoding="utf-8")
    seen: list[str] = []
    for m in PLATFORM_SYS_RE.finditer(text):
        sys_name = m.group(1).strip()
        if sys_name and sys_name not in seen:
            seen.append(sys_name)
    return seen


def parse_meta_field_dat(path: Path, field_re: re.Pattern) -> tuple[dict[str, str], dict[str, str]]:
    """Returns (by_name, by_crc) for a libretro-database metadat field file."""
    by_name: dict[str, str] = {}
    by_crc: dict[str, str] = {}
    if not path.is_file():
        return by_name, by_crc
    in_game = False
    cur_name: str | None = None
    cur_val: str | None = None
    cur_crcs: list[str] = []
    with path.open("r", encoding="utf-8", errors="replace") as f:
        for line in f:
            if not in_game:
                if GAME_START_RE.match(line):
                    in_game = True
                    cur_name = None
                    cur_val = None
                    cur_crcs = []
                continue
            if line.strip() == ")":
                if cur_val:
                    if cur_name:
                        by_name[cur_name] = cur_val
                    for c in cur_crcs:
                        by_crc[c] = cur_val
                in_game = False
                continue
            if cur_name is None and not ROM_LINE_RE.match(line):
                mn = NAME_RE.search(line)
                if mn:
                    cur_name = unescape_dat(mn.group(1))
                    continue
            if cur_val is None:
                mv = field_re.search(line)
                if mv:
                    cur_val = unescape_dat(mv.group(1))
                    continue
            if "crc " in line or "crc\t" in line:
                for mc in CRC_RE.finditer(line):
                    cur_crcs.append(mc.group(1).upper())
    return by_name, by_crc


def parse_hash_dat(path: Path, hashes: dict[str, str]) -> int:
    if not path.is_file():
        return 0
    added = 0
    in_game = False
    cur_name: str | None = None
    with path.open("r", encoding="utf-8", errors="replace") as f:
        for line in f:
            if not in_game:
                if GAME_START_RE.match(line):
                    in_game = True
                    cur_name = None
                continue
            if line.strip() == ")":
                in_game = False
                cur_name = None
                continue
            if cur_name is None and not ROM_LINE_RE.match(line):
                mn = NAME_RE.search(line)
                if mn:
                    cur_name = unescape_dat(mn.group(1))
                    continue
            if cur_name and ("crc " in line or "crc\t" in line):
                for mc in CRC_RE.finditer(line):
                    crc = mc.group(1).upper()
                    if crc not in hashes:
                        hashes[crc] = cur_name
                        added += 1
    return added


def build_meta_for_system(db_repo: Path, system: str) -> tuple[int, int, int]:
    """Generate meta/<safe_system>.tsv. Returns (hash_count, meta_count, raw_bytes)."""
    hashes: dict[str, str] = {}
    hash_dirs = [
        db_repo / "metadat/no-intro",
        db_repo / "metadat/redump",
        db_repo / "dat",
        db_repo / "metadat/fbneo-split",
        db_repo / "metadat/mame-split",
        db_repo / "metadat/mame",
        db_repo / "metadat/tosec",
        db_repo / "metadat/hacks",
    ]
    for d in hash_dirs:
        p = d / f"{system}.dat"
        if p.is_file():
            parse_hash_dat(p, hashes)

    devs_n, devs_c = parse_meta_field_dat(db_repo / "metadat/developer" / f"{system}.dat", DEV_RE)
    pubs_n, pubs_c = parse_meta_field_dat(db_repo / "metadat/publisher" / f"{system}.dat", PUB_RE)
    genres_n, genres_c = parse_meta_field_dat(db_repo / "metadat/genre" / f"{system}.dat", GENRE_RE)
    years_n, years_c = parse_meta_field_dat(db_repo / "metadat/releaseyear" / f"{system}.dat", YEAR_RE)
    months_n, months_c = parse_meta_field_dat(db_repo / "metadat/releasemonth" / f"{system}.dat", MONTH_RE)

    # Also backfill metadata from CRC matches into canonical game names
    for crc, cname in hashes.items():
        if cname not in devs_n and crc in devs_c:
            devs_n[cname] = devs_c[crc]
        if cname not in pubs_n and crc in pubs_c:
            pubs_n[cname] = pubs_c[crc]
        if cname not in genres_n and crc in genres_c:
            genres_n[cname] = genres_c[crc]
        if cname not in years_n and crc in years_c:
            years_n[cname] = years_c[crc]
        if cname not in months_n and crc in months_c:
            months_n[cname] = months_c[crc]

    meta_names = sorted(set(devs_n) | set(pubs_n) | set(genres_n) | set(years_n))
    if not hashes and not meta_names:
        return (0, 0, 0)

    out_path = OUT_META / f"{sanitize_file_name(system)}.tsv"
    lines: list[str] = []
    for crc in sorted(hashes.keys()):
        lines.append(f"H\t{crc}\t{hashes[crc]}\n")
    for name in meta_names:
        y = years_n.get(name, "")
        m = months_n.get(name, "")
        date = f"{y}-{m.zfill(2)}" if (y and m) else y
        d = devs_n.get(name, "")
        p = pubs_n.get(name, "")
        g = genres_n.get(name, "")
        if d or p or g or date:
            lines.append(f"M\t{name}\t{d}\t{p}\t{g}\t{date}\n")

    out_path.write_text("".join(lines), encoding="utf-8")
    return (len(hashes), len(meta_names), out_path.stat().st_size)


def fetch_boxart_for_system(system: str, work_dir: Path, ssh_cmd: str) -> tuple[str, int, int]:
    """Fetch Named_Boxarts/*.png list via blobless git clone (with retry) -> boxart/<safe_system>.txt."""
    repo_name = system.replace(" ", "_")
    out_path = OUT_BOXART / f"{sanitize_file_name(system)}.txt"
    url = f"git@github.com:libretro-thumbnails/{repo_name}.git"
    env = os.environ.copy()
    env["GIT_SSH_COMMAND"] = ssh_cmd

    for attempt in range(3):
        repo_dir = work_dir / f"{sanitize_file_name(system)}_{attempt}"
        if repo_dir.exists():
            shutil.rmtree(repo_dir, ignore_errors=True)
        try:
            proc = subprocess.run(
                ["git", "clone", "--depth", "1", "--filter=blob:none", "--no-checkout", url, str(repo_dir)],
                env=env,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                text=True,
                timeout=60,
            )
            if proc.returncode != 0:
                err = proc.stderr or ""
                # Repository genuinely does not exist -> don't retry
                if "Repository not found" in err or "not found" in err.lower():
                    return (system, 0, 0)
                time.sleep(1.0 + attempt)
                continue
            ls = subprocess.run(
                ["git", "-C", str(repo_dir), "ls-tree", "--name-only", "HEAD:Named_Boxarts"],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                timeout=30,
            )
            shutil.rmtree(repo_dir, ignore_errors=True)
            if ls.returncode != 0:
                return (system, 0, 0)
            names = sorted({ln.strip() for ln in ls.stdout.splitlines() if ln.strip().lower().endswith(".png")})
            if not names:
                return (system, 0, 0)
            out_path.write_text("\n".join(names) + "\n", encoding="utf-8")
            return (system, len(names), out_path.stat().st_size)
        except Exception:
            shutil.rmtree(repo_dir, ignore_errors=True)
            time.sleep(1.0 + attempt)
    return (system, 0, 0)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--db-repo",
        type=Path,
        default=None,
        help="Path to cloned libretro-database repo (auto-cloned to temp dir if omitted)",
    )
    parser.add_argument("--skip-boxart", action="store_true")
    parser.add_argument("--skip-meta", action="store_true")
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument(
        "--ssh-cmd",
        default="ssh -o Hostname=ssh.github.com -p 443 -o BatchMode=yes -o ConnectTimeout=15",
    )
    args = parser.parse_args()

    temp_db_dir: Path | None = None
    db_repo = args.db_repo
    if not args.skip_meta and db_repo is None:
        temp_db_dir = Path(tempfile.mkdtemp(prefix="pocketdeck_libretro_db_"))
        db_repo = temp_db_dir / "libretro-database"
        print(f"Cloning libretro-database into {db_repo} ...")
        env = os.environ.copy()
        env["GIT_SSH_COMMAND"] = args.ssh_cmd
        subprocess.run(
            ["git", "clone", "--depth", "1", "git@github.com:libretro/libretro-database.git", str(db_repo)],
            env=env,
            check=True,
        )

    systems = load_systems_from_catalog()
    print(f"Found {len(systems)} unique libretro systems in PlatformCatalog.kt")

    # Clean old files (including any old .gz files)
    if not args.skip_meta:
        shutil.rmtree(OUT_META, ignore_errors=True)
        OUT_META.mkdir(parents=True, exist_ok=True)
    if not args.skip_boxart:
        shutil.rmtree(OUT_BOXART, ignore_errors=True)
        OUT_BOXART.mkdir(parents=True, exist_ok=True)

    if not args.skip_meta:
        total_h = 0
        total_m = 0
        total_b = 0
        hit_sys = 0
        for s in systems:
            h, m, b = build_meta_for_system(db_repo, s)
            if h or m:
                hit_sys += 1
                total_h += h
                total_m += m
                total_b += b
                print(f"  [META] {s}: {h} hashes, {m} metas ({b/1024:.1f} KB)")
        print(f"META DONE: {hit_sys}/{len(systems)} systems, {total_h} hashes, {total_m} metas, {total_b/1024/1024:.2f} MB raw\n")

    if not args.skip_boxart:
        work_dir = Path(tempfile.mkdtemp(prefix="pocketdeck_boxart_"))
        total_c = 0
        total_b = 0
        hit_sys = 0
        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
                futs = {pool.submit(fetch_boxart_for_system, s, work_dir, args.ssh_cmd): s for s in systems}
                for fut in concurrent.futures.as_completed(futs):
                    s = futs[fut]
                    try:
                        _, count, b = fut.result()
                        if count > 0:
                            hit_sys += 1
                            total_c += count
                            total_b += b
                            print(f"  [BOXART] {s}: {count} covers ({b/1024:.1f} KB)")
                        else:
                            print(f"  [BOXART] {s}: (none / skipped)")
                    except Exception as e:
                        print(f"  [BOXART] {s}: ERROR {e}")
        finally:
            shutil.rmtree(work_dir, ignore_errors=True)
        print(f"BOXART DONE: {hit_sys}/{len(systems)} systems, {total_c} covers, {total_b/1024/1024:.2f} MB raw")

    if temp_db_dir is not None:
        shutil.rmtree(temp_db_dir, ignore_errors=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
