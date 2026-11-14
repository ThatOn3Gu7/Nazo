#!/usr/bin/env python3
"""Fail closed when Gradle is assembling an official GitHub tag release."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import sys

TAG_PATTERN = re.compile(r"^v\d+\.\d+(?:\.\d+)?(?:-(?:alpha|beta|rc)\d+)?$")


def validate(
    *,
    version: str,
    version_code: int,
    keystore: str,
    credentials_present: bool,
    environ: dict[str, str],
) -> list[str]:
    # Ordinary local/PR release assemblies are compile checks and may remain
    # unsigned. Only a tag-triggered GitHub build can publish a GitHub Release.
    if environ.get("GITHUB_ACTIONS") != "true" or environ.get("GITHUB_REF_TYPE") != "tag":
        return []

    tag = environ.get("GITHUB_REF_NAME", "")
    errors: list[str] = []
    if not TAG_PATTERN.fullmatch(tag):
        errors.append(f"release tag '{tag}' is not a supported Nazo version tag")
    if tag != f"v{version}":
        errors.append(f"release tag '{tag}' does not match app versionName 'v{version}'")
    if version_code <= 0:
        errors.append("versionCode must be positive")
    if not credentials_present:
        errors.append("all four Android signing properties are required for an official release")
    if not keystore or not Path(keystore).is_file() or Path(keystore).stat().st_size == 0:
        errors.append("the release signing keystore is missing or empty")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--keystore", default="")
    parser.add_argument("--credentials-present", action="store_true")
    args = parser.parse_args()

    errors = validate(
        version=args.version,
        version_code=args.version_code,
        keystore=args.keystore,
        credentials_present=args.credentials_present,
        environ=dict(os.environ),
    )
    if errors:
        for error in errors:
            print(f"RELEASE BLOCKED: {error}", file=sys.stderr)
        return 1
    print("Release guard passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
