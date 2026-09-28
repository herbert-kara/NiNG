"""A step header at the wrong indent is not a syntax error, and that is the problem.

PyYAML reads a step written one column left as part of the previous step's run block: the text
becomes a string, the document still parses, and the job quietly has fewer steps than it names.
GitHub's own parser then refuses the file outright -- "'env' is already defined" -- and the
workflow cannot be dispatched at all.

That is what happened to the sync: four steps on disk, three under PyYAML, and an HTTP 422 when
the release path tried to use it. So the check is not that the file parses, because it parses
either way. It is that every step GitHub would see has a name it can read, a run body or a uses,
and no duplicated key -- and that the step count a step-level guard expects is the count present.

Run from the repository root:  python3 tools/verify_workflow_steps.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
WORKFLOWS = ROOT / ".github" / "workflows"

# The steps these workflows must keep having. Each one is a stage that can fail on its own, so a
# merge that swallowed one would leave a release published without its checks, or a check run
# without its release, and both read as success.
EXPECTED_STEPS = {
    "upstream-sync.yml": {
        "sync": {"Merge upstream without dropping fork features",
                 "Verify fork features survived the merge",
                 "Push the merge so the branch keeps its history"},
        "release": {"Derive the next -ning version from existing tags",
                    "Build the signed release from the merged commit and wait for it",
                    "Publish it, so the in-app updater can see the new version"},
    },
}


def main() -> int:
    problems: list[str] = []
    for path in sorted(WORKFLOWS.glob("*.yml")):
        try:
            doc = yaml.safe_load(path.read_text("utf-8"))
        except yaml.YAMLError as exc:
            problems.append(f"{path.name}: does not parse: {exc}")
            continue
        if not isinstance(doc, dict) or "jobs" not in doc:
            problems.append(f"{path.name}: has no jobs key")
            continue
        for job_name, job in doc["jobs"].items():
            if "uses" in job:
                # A reusable-workflow job has no steps: it calls another workflow, and GitHub
                # rejects a steps list on the same job. Reading the absence of steps as a
                # mis-indented header would send you looking for a line that is not wrong.
                continue
            steps = job.get("steps")
            if not isinstance(steps, list) or not steps:
                problems.append(
                    f"{path.name} job {job_name}: no steps list. A step header at the wrong indent "
                    "is absorbed into the previous run block, which parses and then leaves the job "
                    "with fewer steps than it names"
                )
                continue
            for index, step in enumerate(steps, 1):
                where = f"{path.name} job {job_name} step {index}"
                if not isinstance(step, dict):
                    problems.append(f"{where}: not a mapping, which is what a mis-indented "
                                    "step header parses as")
                    continue
                if "uses" not in step and "run" not in step:
                    problems.append(f"{where}: has neither uses nor run")
                if "uses" in step and "run" in step:
                    problems.append(f"{where}: has both uses and run")
                if "name" in step and not str(step["name"]).strip():
                    problems.append(f"{where}: has an empty name")
            names = {s.get("name") for s in steps if isinstance(s, dict)}
            for required in EXPECTED_STEPS.get(path.name, {}).get(job_name, set()):
                if required not in names:
                    problems.append(
                        f"{path.name} job {job_name}: step {required!r} is gone. Each of these is a "
                        "stage that can fail on its own, so one swallowed by an indent takes a "
                        "release path down with it -- the sync merged, verified, and published "
                        "nothing, which looks exactly like upstream having nothing new"
                    )
    for problem in problems:
        print("::error::" + problem)
    if problems:
        print(f"{len(problems)} workflow problem(s)", file=sys.stderr)
        return 1
    print("every workflow parses, and every step GitHub would see is present")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
