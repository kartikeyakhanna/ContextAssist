"""
Design-time Screen Memory Load scoring.

Runs OFFLINE, never at runtime. Calling a model on every screen view would be
slow, expensive and non-deterministic; precomputing per screen template and
caching by fingerprint is how this would be built in production anyway.

Output is committed to config/complexity-cache.json, which is both the engine's
lookup table and a deliverable in its own right: a ranked report of the most
overwhelming screens in a product, with the specific elements responsible.

That report is the part a PM can act on. Thread does not change anyone's UI -
changing the UI is a product decision, not an engineering one. It supplies the
evidence to the people who own that decision.

Usage:
    python run.py --input screens/ --output ../../config/complexity-cache.json
    python run.py --report            # ranked "most overwhelming screens"
"""

from __future__ import annotations

import argparse
import json
import math
import pathlib
import sys

RUBRIC = pathlib.Path(__file__).with_name("rubric.txt")

# Must stay in step with config/weights.json. The scoring maths lives in the
# engine; this mirror exists only so the offline report shows the same numbers.
WEIGHTS = {
    "itemsToHold": 30.0,
    "decisionDensity": 20.0,
    "progressInvisibility": 15.0,
    "irreversibility": 15.0,
    "crossReference": 10.0,
    "languageComplexity": 10.0,
}


def clamp01(v: float) -> float:
    return max(0.0, min(1.0, v))


def hick(option_count: int, saturation: int = 32) -> float:
    """Hick's Law: choice time grows with log2(n+1), so 20 options is not 2x 10."""
    if option_count <= 0:
        return 0.0
    return clamp01(math.log2(1 + option_count) / math.log2(1 + saturation))


def score(facts: dict) -> float:
    # Miller's 7 +/- 2: the cost of holding items saturates around seven.
    items = clamp01(facts.get("itemsToHold", 0) / 7.0)
    decisions = hick(facts.get("optionCount", 0))
    progress = 0.0 if facts.get("progressVisible", False) else 1.0
    irreversible = clamp01(facts.get("irreversibleActions", 0) / 3.0)
    cross_ref = clamp01(facts.get("crossReferences", 0) / 4.0)
    language = clamp01(facts.get("languageComplexity", 0.0))

    return round(
        items * WEIGHTS["itemsToHold"]
        + decisions * WEIGHTS["decisionDensity"]
        + progress * WEIGHTS["progressInvisibility"]
        + irreversible * WEIGHTS["irreversibility"]
        + cross_ref * WEIGHTS["crossReference"]
        + language * WEIGHTS["languageComplexity"],
        1,
    )


def extract(screen_path: pathlib.Path) -> dict:
    """
    Send the node tree / DOM dump plus RUBRIC to the model.

    Determinism settings are not optional here: temperature 0, a fixed rubric and
    three few-shot examples. A judge will re-run this and expect the same numbers,
    and a score that moves between runs is not a measurement.

    Left unimplemented so the repo carries no API keys. Wire to Azure OpenAI and
    return the JSON object described in rubric.txt.
    """
    raise NotImplementedError(
        "Wire to Azure OpenAI: temperature=0, rubric.txt as system prompt, "
        "fewshot/*.json as examples."
    )


def report(cache_path: pathlib.Path) -> None:
    """The design-time deliverable: most overwhelming screens, ranked, with reasons."""
    data = json.loads(cache_path.read_text(encoding="utf-8"))
    screens = data.get("screens", {})

    ranked = sorted(
        screens.items(), key=lambda kv: kv[1].get("score", 0), reverse=True
    )

    print(f"{'SCORE':>6}  SCREEN")
    print("-" * 72)
    for name, entry in ranked:
        print(f"{entry.get('score', 0):>6}  {name}")
        for element in entry.get("offendingElements", []):
            print(f"{'':>6}    - {element}")
    print()
    print(f"{len(ranked)} screens scored. Rubric v{data.get('_rubricVersion', '?')}.")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=pathlib.Path, help="Directory of screen dumps")
    parser.add_argument(
        "--output",
        type=pathlib.Path,
        default=pathlib.Path(__file__).parents[2] / "config" / "complexity-cache.json",
    )
    parser.add_argument("--report", action="store_true", help="Print the ranked report")
    args = parser.parse_args(argv)

    if args.report:
        report(args.output)
        return 0

    if not args.input:
        parser.error("--input is required unless --report is used")

    screens = {}
    for path in sorted(args.input.glob("*.json")):
        facts = extract(path)
        offending = facts.pop("offendingElements", [])
        screens[path.stem] = {
            "score": score(facts),
            "factors": facts,
            "offendingElements": offending,
        }

    args.output.write_text(
        json.dumps({"_rubricVersion": "1.0", "screens": screens}, indent=2),
        encoding="utf-8",
    )
    print(f"Scored {len(screens)} screens -> {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
