#!/usr/bin/env python3
"""Check a deals.json / catalog.json before the workflow commits it (stdlib only).
Usage: python tools/validate_data.py NEW.json [OLD.json]
Fails (exit 1) when the file does not parse, an entry list shrank compared with OLD, a link is not https://, or a text is
over 400 characters / has control characters / looks like an instruction to an AI. The rules live in refresh_deals.validate_data."""
import json
import sys

sys.argv, _args = sys.argv[:1], sys.argv[1:]
import refresh_deals as rd  # noqa: E402


def main(args):
    if not args:
        print(__doc__)
        return 2
    try:
        with open(args[0]) as f:
            new = json.load(f)
        old = None
        if len(args) > 1:
            with open(args[1]) as f:
                old = json.load(f)
    except Exception as e:
        print(f"cannot parse: {e}")
        return 1
    problems = rd.validate_data(new, old)
    for p in problems[:30]:
        print(p)
    print("OK" if not problems else f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(_args))
