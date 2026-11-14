#!/usr/bin/env python3
"""Verify Android build-tools reports for the exact signed release APK."""
import re
import sys
from pathlib import Path


def verify(signature, badging, certificate, code, name):
    certs = re.findall(r'^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$', signature, re.M)
    if [c.upper() for c in certs] != [certificate.upper()] or not re.fullmatch(r'[0-9a-fA-F]{64}', certificate):
        raise ValueError('APK upload certificate does not match expected certificate')
    package_line = next((line for line in badging.splitlines() if line.startswith('package: ')), '')
    attrs = dict(re.findall(r"\b(\w+)='([^']*)'", package_line))
    expected = {'name': 'com.iumrah.app', 'versionCode': code, 'versionName': name}
    if any(attrs.get(key) != value for key, value in expected.items()):
        raise ValueError('Incorrect release APK identity')
    if any(line.strip() == 'application-debuggable' for line in badging.splitlines()):
        raise ValueError('Release APK is debuggable')


if __name__ == '__main__':
    try:
        signature, badging, certificate, code, name = sys.argv[1:]
        verify(Path(signature).read_text(), Path(badging).read_text(), certificate, code, name)
    except (ValueError, OSError) as exc:
        raise SystemExit(str(exc))
    print('Verified signed APK identity and upload certificate.')
