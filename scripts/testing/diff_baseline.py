#!/usr/bin/env python3
"""Compare an in-game harness run against a committed baseline.

`parallel_run.py` answers "what happened". This answers "what *changed*", which is the question you
actually have when deciding whether a diff is safe to merge. A wall of thirty statuses does not
tell you that `logs-axes` used to pass; a line reading `logs-axes  PASS -> TIMEOUT` does.

Three kinds of change are reported, and all three are load-bearing:

*Verdicts.* A scenario that used to pass and now does not is a regression. A scenario that used to
fail and now passes is worth shouting about too, because known-defect scenarios are how fixes are
supposed to announce themselves.

*Timing.* Per-scenario tick counts and suite wall clock. This exists because performance is a stated
goal of the fork (FORK-NOTES.md) and because a real regression has already hidden here: a change
that made one scenario green took the suite from 203s to 427s, and the only symptom visible in the
verdicts was an unrelated scenario flaking. A differ that reported pass/fail alone would have shown
the flake and hidden the cause.

*Flakiness.* A curated run gives every instance the same scenario list, so one run yields several
independent samples of each scenario. Disagreement between them is a fact about the scenario, not
noise to be averaged away -- it is how a marginal case announces itself before it becomes a failure.

Exit status is 0 when nothing regressed and 1 when something did, so this can gate a merge.
"""

import argparse
import json
import statistics
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_REPORT = REPO_ROOT / "dist" / "testing" / "parallel-latest.json"
DEFAULT_BASELINE = Path(__file__).resolve().parent / "baseline.json"

# How much a scenario's median tick count may move before it is called a change. Scenario timings
# are genuinely noisy -- chunk loading, pathfinding thread scheduling -- so a tight threshold would
# cry wolf, and a suite that cries wolf gets ignored. 25% is wide enough to sit above that noise and
# narrow enough to have caught the 2x suite slowdown that motivated this script.
DEFAULT_TICK_TOLERANCE = 0.25

PASS = "PASS"


def aggregate(report):
    """Collapse a report's per-instance rows into one record per scenario name."""
    by_name = {}
    for scenario in report.get("scenarios", []):
        by_name.setdefault(scenario["name"], []).append(scenario)

    out = {}
    for name, runs in sorted(by_name.items()):
        statuses = sorted({run["status"] for run in runs})
        ticks = [run["ticks"] for run in runs if isinstance(run.get("ticks"), int)]
        out[name] = {
            # A scenario counts as passing only if it passed everywhere. Two greens and a red is a
            # red -- averaging that away is how a marginal scenario becomes an unexplained failure
            # three weeks later.
            "status": PASS if statuses == [PASS] else next(s for s in statuses if s != PASS),
            "statuses": statuses,
            "runs": len(runs),
            "medianTicks": int(statistics.median(ticks)) if ticks else None,
            "minTicks": min(ticks) if ticks else None,
            "maxTicks": max(ticks) if ticks else None,
        }
    return out


def load(path, what):
    try:
        return json.loads(Path(path).read_text())
    except FileNotFoundError:
        sys.exit(f"no {what} at {path}")
    except json.JSONDecodeError as exc:
        sys.exit(f"{what} at {path} is not valid JSON: {exc}")


def pct(new, old):
    if not old:
        return None
    return (new - old) / old


def fmt_delta(new, old):
    change = pct(new, old)
    if change is None:
        return f"{old} -> {new}"
    return f"{old} -> {new} ({change:+.0%})"


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--report", default=DEFAULT_REPORT,
                        help="merged run to judge (default: dist/testing/parallel-latest.json)")
    parser.add_argument("--baseline", default=DEFAULT_BASELINE,
                        help="baseline to compare against (default: scripts/testing/baseline.json)")
    parser.add_argument("--update", action="store_true",
                        help="overwrite the baseline with this report and exit")
    parser.add_argument("--tick-tolerance", type=float, default=DEFAULT_TICK_TOLERANCE,
                        help="fractional tick-count change before it is reported (default: 0.25)")
    parser.add_argument("--fail-on-timing", action="store_true",
                        help="treat a tick-count regression as a failure, not just a note")
    args = parser.parse_args()

    report = load(args.report, "report")
    current = aggregate(report)

    if args.update:
        payload = {
            "wallClockSeconds": report.get("wallClockSeconds"),
            "instances": len(report.get("instances", [])),
            "scenarios": current,
        }
        Path(args.baseline).write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")
        passed = sum(1 for s in current.values() if s["status"] == PASS)
        print(f"baseline updated: {passed}/{len(current)} scenarios passing, "
              f"{report.get('wallClockSeconds')}s wall clock -> {args.baseline}")
        return 0

    baseline = load(args.baseline, "baseline")
    previous = baseline.get("scenarios", {})

    regressions, improvements, notes = [], [], []

    for name in sorted(set(previous) | set(current)):
        was, now = previous.get(name), current.get(name)

        if was is None:
            notes.append(f"NEW       {name:<34} {now['status']}")
            continue
        if now is None:
            notes.append(f"GONE      {name:<34} was {was['status']}")
            continue

        if was["status"] != now["status"]:
            line = f"{name:<34} {was['status']} -> {now['status']}"
            (improvements if now["status"] == PASS else regressions).append(line)
        elif len(now["statuses"]) > 1:
            # Same overall verdict, but the instances disagreed. Worth surfacing even when the
            # aggregate is unchanged: this is what a scenario looks like just before it breaks.
            notes.append(f"FLAKY     {name:<34} {'/'.join(now['statuses'])} "
                         f"across {now['runs']} runs")

        old_ticks, new_ticks = was.get("medianTicks"), now.get("medianTicks")
        if old_ticks and new_ticks:
            change = pct(new_ticks, old_ticks)
            if abs(change) >= args.tick_tolerance:
                line = f"{name:<34} ticks {fmt_delta(new_ticks, old_ticks)}"
                (regressions if change > 0 else improvements).append(line)

    old_wall, new_wall = baseline.get("wallClockSeconds"), report.get("wallClockSeconds")
    wall_line = None
    if old_wall and new_wall:
        change = pct(new_wall, old_wall)
        if abs(change) >= args.tick_tolerance:
            wall_line = f"suite wall clock {fmt_delta(new_wall, old_wall)}"
            (regressions if change > 0 else improvements).append(wall_line)

    passed = sum(1 for s in current.values() if s["status"] == PASS)
    was_passed = sum(1 for s in previous.values() if s["status"] == PASS)
    print(f"{passed}/{len(current)} passing (baseline {was_passed}/{len(previous)}), "
          f"{new_wall}s wall clock (baseline {old_wall}s)")
    print()

    for title, rows in (("REGRESSED", regressions), ("IMPROVED", improvements), ("NOTES", notes)):
        if rows:
            print(f"{title}:")
            for row in rows:
                print(f"  {row}")
            print()

    if not (regressions or improvements or notes):
        print("no change against baseline")

    # A timing move alone is a note by default. It is real signal, but it is also the noisiest, and
    # a gate that blocks on it without --fail-on-timing would be turned off within a week.
    verdict_regressions = [r for r in regressions if "ticks " not in r and r != wall_line]
    if verdict_regressions or (args.fail_on_timing and regressions):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
