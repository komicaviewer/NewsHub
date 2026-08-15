#!/usr/bin/env python3
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import sys


class CostReservationError(RuntimeError):
    pass


def reserve_release(
    state: dict[str, object],
    *,
    now: datetime,
    build_minutes: int,
    monthly_build_limit: int,
    monthly_release_limit: int,
) -> dict[str, object]:
    if build_minutes != 20:
        raise CostReservationError("app release reservation must be exactly 20 build-minutes")
    if not 1 <= monthly_release_limit <= 4:
        raise CostReservationError("monthly app release limit must be between 1 and 4")
    month = now.astimezone(timezone.utc).strftime("%Y-%m")
    if state.get("month") != month:
        state = {
            "month": month,
            "buildMinutes": 0,
            "repairJobs": 0,
            "hostRepairJobs": 0,
            "appReleaseJobs": 0,
            "days": {},
        }
    result = dict(state)
    build_total = int(result.get("buildMinutes", 0)) + build_minutes
    release_total = int(result.get("appReleaseJobs", 0)) + 1
    if build_total > monthly_build_limit:
        raise CostReservationError("monthly Cloud Build budget is exhausted")
    if release_total > monthly_release_limit:
        raise CostReservationError("monthly NewsHub app release budget is exhausted")
    result["buildMinutes"] = build_total
    result["appReleaseJobs"] = release_total
    result["updatedAt"] = now.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--monthly-build-limit", type=int, required=True)
    parser.add_argument("--monthly-release-limit", type=int, required=True)
    parser.add_argument("--now")
    args = parser.parse_args()
    try:
        state = json.loads(args.state.read_text(encoding="utf-8")) if args.state.exists() else {}
        if not isinstance(state, dict):
            raise CostReservationError("cost state must be an object")
        now = (
            datetime.fromisoformat(args.now.replace("Z", "+00:00"))
            if args.now
            else datetime.now(timezone.utc)
        )
        result = reserve_release(
            state,
            now=now,
            build_minutes=20,
            monthly_build_limit=args.monthly_build_limit,
            monthly_release_limit=args.monthly_release_limit,
        )
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except (OSError, ValueError, json.JSONDecodeError, CostReservationError) as exc:
        print(f"release cost reservation failed: {exc}", file=sys.stderr)
        return 1
    print(
        f"reserved appReleaseJobs={result['appReleaseJobs']} "
        f"buildMinutes={result['buildMinutes']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
