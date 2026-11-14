#!/usr/bin/env python3
"""Validate explicit Play version inputs before using them in Gradle or file names."""
import re
import sys


def validate(code, name):
    if not re.fullmatch(r'[1-9][0-9]{0,9}', code) or not 15 <= int(code) <= 2_100_000_000:
        raise ValueError('versionCode must be a canonical decimal integer from 15 to 2100000000; also check Play Console history')
    if not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._-]{0,79}', name):
        raise ValueError('versionName must contain 1–80 ASCII letters, digits, dots, underscores or hyphens, starting with a letter/digit')


if __name__ == '__main__':
    try:
        if len(sys.argv) != 3:
            raise ValueError('Usage: release_input.py <versionCode> <versionName>')
        validate(sys.argv[1], sys.argv[2])
    except ValueError as exc:
        raise SystemExit(str(exc))
