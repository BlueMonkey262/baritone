#!/usr/bin/env python3
"""Run the Baritone in-game test harness across several cloned PrismLauncher instances.

The harness itself runs one scenario at a time inside one Minecraft client. Getting through
several hundred scenarios in a sitting means running several clients at once, which is what this
does: clone the base instance N times, give each clone a shard of the scenario list, launch them
all, wait for their reports, and merge the results into one summary.

Two things here are less obvious than they look.

*Reaping.* A finished instance does not reliably exit. The harness disconnects and calls
Minecraft.stop(), the log says "Stopping!", and the JVM has been observed sitting resident for
minutes afterwards holding ~2GB. Waiting for process exit as the completion signal therefore hangs
forever, and leaving the corpses around exhausts memory after a couple of rounds. So completion is
detected by the harness's own "complete" flag inside latest.json -- not by the file appearing, which
happens seconds in, because the harness rewrites it after every scenario -- and the process is
killed once that flag is set.

*Worlds are copied, not shared.* Each clone gets its own copy of the world, because two Minecraft
clients writing one save directory corrupt it. The arena slots the harness reuses keep that copy
from growing without bound.
"""

import argparse
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
from pathlib import Path

PRISM_ROOT = Path(
    "/home/eli/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances"
)
DEFAULT_INSTANCE = "baritone-testing"
FLATPAK_APP = "org.prismlauncher.PrismLauncher"

# Copied from the base instance; everything else (logs, other worlds, reports) is left behind.
SKIP_DIRS = {"logs", "crash-reports", "screenshots"}


VERSION_SUFFIX = re.compile(r"(\d+\.\d+(?:\.\d+)*)$")


def trailing_version(instance):
    """The Minecraft version an instance name ends in, if any.

    Instances are named `tenor-testing-<mcversion>`, so the version is recoverable without a second
    flag. Returns None for names that do not carry one -- notably the pre-v0.2 `baritone-testing`,
    which is how a run against an unlabelled instance still works and simply records no version.
    """
    match = VERSION_SUFFIX.search(instance)
    return match.group(1) if match else None


def instance_dir(name):
    return PRISM_ROOT / name


def minecraft_dir(name):
    return instance_dir(name) / "minecraft"


def report_dir(name):
    return minecraft_dir(name) / "baritone" / "testing"


def running_pids(name):
    """PIDs of Minecraft JVMs belonging to an instance.

    Matched on the java process's own argv rather than a bare pattern search: the instance path
    also appears in file managers, editors and in this script's own command line, and killing one
    of those would be a genuinely bad outcome.
    """
    pids = []
    out = subprocess.run(
        ["ps", "-eo", "pid,comm,args"], capture_output=True, text=True
    ).stdout
    for line in out.splitlines()[1:]:
        parts = line.split(None, 2)
        if len(parts) < 3:
            continue
        pid, comm, args = parts
        if comm == "java" and f"instances/{name}/" in args:
            pids.append(int(pid))
    return pids


def clone(base, target, world, jar):
    """Make target a copy of base carrying only the given world."""
    dest = instance_dir(target)
    if dest.exists():
        shutil.rmtree(dest)
    src = instance_dir(base)

    def ignore(directory, entries):
        skipped = set()
        for entry in entries:
            full = Path(directory) / entry
            if entry in SKIP_DIRS:
                skipped.add(entry)
            elif full.parent.name == "saves" and entry != world:
                skipped.add(entry)
        return skipped

    shutil.copytree(src, dest, ignore=ignore, symlinks=True)

    # A stale session.lock from the copied world makes Minecraft think the save is in use.
    for lock in (dest / "minecraft" / "saves").rglob("session.lock"):
        lock.unlink(missing_ok=True)

    # Rename, or PrismLauncher shows N instances all called the same thing.
    cfg = dest / "instance.cfg"
    if cfg.exists():
        text = cfg.read_text().replace(f"name={base}", f"name={target}")
        cfg.write_text(text)

    mods = dest / "minecraft" / "mods"
    for pattern in ("baritone-*.jar", "continuo-*.jar"):
        for old in mods.glob(pattern):
            old.unlink()
    shutil.copy2(jar, mods / Path(jar).name)

    # Reports from the base instance would otherwise be mistaken for this run's output.
    reports = report_dir(target)
    if reports.exists():
        shutil.rmtree(reports)
    reports.mkdir(parents=True, exist_ok=True)


def write_shard(name, scenarios):
    flag = report_dir(name) / "autorun.flag"
    flag.parent.mkdir(parents=True, exist_ok=True)
    flag.write_text("\n".join(scenarios) + "\n")


def launch(name, world):
    return subprocess.Popen(
        ["flatpak", "run", FLATPAK_APP, "--launch", name, "--world", world],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )


def latest_report(name, finished_only=True):
    """The instance's report, or None.

    The harness rewrites latest.json after *every* scenario so a crash cannot throw away a whole
    shard, which means the file existing is not a completion signal -- it appears seconds into a
    run. Completion is the "complete" flag the harness sets when the suite ends. Passing
    finished_only=False is how partial results are harvested from an instance that timed out.
    """
    path = report_dir(name) / "latest.json"
    if not path.exists():
        return None
    try:
        report = json.loads(path.read_text())
    except (json.JSONDecodeError, OSError):
        return None  # caught mid-write
    if finished_only and not report.get("complete"):
        return None
    return report


def reap(name, grace=10):
    for pid in running_pids(name):
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            continue
    deadline = time.time() + grace
    while time.time() < deadline and running_pids(name):
        time.sleep(0.5)
    for pid in running_pids(name):
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


def shard(scenarios, count):
    """Round-robin, so a slow family of scenarios spreads across instances instead of landing
    entirely on one."""
    buckets = [[] for _ in range(count)]
    for i, scenario in enumerate(scenarios):
        buckets[i % count].append(scenario)
    return [b for b in buckets if b]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-n", "--instances", type=int, default=4)
    parser.add_argument("-w", "--world", default="testing2")
    parser.add_argument(
        "--instance",
        default=DEFAULT_INSTANCE,
        help=f"base PrismLauncher instance to clone (default: {DEFAULT_INSTANCE}). "
             "Each supported Minecraft version needs its own, with its own world: a save written "
             "by a later version will not open in an earlier one.",
    )
    parser.add_argument(
        "--mc-version",
        help="Minecraft version label recorded in the merged report and used to pick the "
             "baseline file. Defaults to the trailing version in --instance, if it has one.",
    )
    parser.add_argument("--jar", help="path to the Continuo jar (default: newest unoptimized fabric jar in dist/)")
    parser.add_argument("--timeout", type=int, default=3600, help="seconds to wait for a report")
    parser.add_argument("--scenarios", help="file with one scenario name per line")
    parser.add_argument("--fuzz", help="inclusive seed range, e.g. 1-240")
    parser.add_argument("--curated", action="store_true", help="leave shards empty: each instance runs the curated suite")
    parser.add_argument("--keep", action="store_true", help="do not delete clones afterwards")
    args = parser.parse_args()

    base = args.instance
    if not instance_dir(base).exists():
        sys.exit(f"no such instance: {instance_dir(base)}")
    mc_version = args.mc_version or trailing_version(base)

    if running_pids(base):
        sys.exit(f"{base} is running; close it first")

    jar = args.jar
    if not jar:
        candidates = sorted(
            Path("dist").glob("continuo-unoptimized-fabric-*.jar"),
            key=lambda p: p.stat().st_mtime,
        )
        if not candidates:
            sys.exit("no unoptimized fabric jar in dist/; build first")
        jar = str(candidates[-1])

    scenarios = []
    if args.scenarios:
        scenarios = [
            line.strip()
            for line in Path(args.scenarios).read_text().splitlines()
            if line.strip() and not line.startswith("#")
        ]
    elif args.fuzz:
        low, high = (int(part) for part in args.fuzz.split("-"))
        scenarios = [f"fuzz-{seed}" for seed in range(low, high + 1)]
    elif not args.curated:
        sys.exit("pass one of --scenarios, --fuzz or --curated")

    shards = shard(scenarios, args.instances) if scenarios else [[]] * args.instances
    names = [f"{base}-{i + 1}" for i in range(len(shards))]

    print(f"base instance: {base}" + (f" (Minecraft {mc_version})" if mc_version else ""))
    print(f"jar: {jar}")
    print(f"world: {args.world}")
    for name, names_in_shard in zip(names, shards):
        print(f"  {name}: {len(names_in_shard) or 'curated suite'} scenario(s)")

    started = time.time()
    for name, names_in_shard in zip(names, shards):
        print(f"cloning {name} ...", flush=True)
        clone(base, name, args.world, jar)
        write_shard(name, names_in_shard)

    procs = {}
    for name in names:
        print(f"launching {name} ...", flush=True)
        procs[name] = launch(name, args.world)
        time.sleep(8)  # stagger: four clients hitting the GPU and disk at once is a slow start

    pending = set(names)
    results = {}
    deadline = started + args.timeout
    while pending and time.time() < deadline:
        time.sleep(5)
        for name in sorted(pending):
            report = latest_report(name)
            if report is None:
                # Not finished. If the JVM has died without ever completing, stop waiting on it.
                if not running_pids(name) and time.time() - started > 120:
                    partial = latest_report(name, finished_only=False)
                    done = len(partial.get("scenarios", [])) if partial else 0
                    print(f"{name}: died after {done} scenario(s)", flush=True)
                    if partial:
                        results[name] = partial
                    pending.discard(name)
                continue
            results[name] = report
            pending.discard(name)
            elapsed = int(time.time() - started)
            print(f"{name}: report after {elapsed}s "
                  f"({report.get('passed')}/{report.get('total')} passed)", flush=True)
            reap(name)

    for name in sorted(pending):
        partial = latest_report(name, finished_only=False)
        done = len(partial.get("scenarios", [])) if partial else 0
        print(f"{name}: TIMED OUT after {args.timeout}s ({done} scenario(s) completed)", flush=True)
        if partial:
            results[name] = partial
        reap(name)

    summarise(results, pending, names, started, mc_version)

    if not args.keep:
        for name in names:
            shutil.rmtree(instance_dir(name), ignore_errors=True)


def summarise(results, pending, names, started, mc_version=None):
    scenarios = []
    for name in names:
        report = results.get(name)
        if not report:
            continue
        for scenario in report.get("scenarios", []):
            scenario["instance"] = name
            scenarios.append(scenario)

    passed = [s for s in scenarios if s["status"] == "PASS"]
    failed = [s for s in scenarios if s["status"] != "PASS"]

    out = Path("dist/testing")
    out.mkdir(parents=True, exist_ok=True)
    merged = out / "parallel-latest.json"
    merged.write_text(json.dumps(
        {
            "mcVersion": mc_version,
            "wallClockSeconds": int(time.time() - started),
            "instances": names,
            "timedOut": sorted(pending),
            "passed": len(passed),
            "total": len(scenarios),
            "scenarios": scenarios,
        },
        indent=2,
    ))

    print()
    print(f"=== {len(passed)}/{len(scenarios)} passed "
          f"in {int(time.time() - started)}s wall clock ===")
    for scenario in failed:
        print(f"  {scenario['status']:<14} {scenario['name']:<18} "
              f"[{scenario['instance']}] {scenario['message']}")
    print(f"merged report: {merged}")


if __name__ == "__main__":
    main()
