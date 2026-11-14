#!/usr/bin/env python3
"""Offline structural guard for the V10 release pipeline; not a release build."""
from pathlib import Path
import ast
import subprocess

root = Path(__file__).resolve().parents[1]
for name in ('release_input.py', 'verify_play_manifest.py', 'verify_release_apk.py', 'tests/test_release_v10.py'):
    ast.parse((root/'scripts'/name).read_text())
builder = root/'scripts/build-play-release.sh'
subprocess.run(['bash','-n',str(builder)],check=True)
text = builder.read_text()
for gate in ('VerifyAabSigner.java', 'verify_release_apk.py', 'verify-16kb-native-libs.sh "$APK"', 'verify-16kb-native-libs.sh "$AAB"', 'zipalign" -c -P 16', 'PAGE_ALIGNMENT_16K', 'validate --bundle=', 'dump manifest --bundle=', 'PLAY_UPLOAD_CERT_SHA256:?', ':app:compileReleaseKotlin', ':app:testDebugUnitTest', ':app:lintRelease', ':app:bundleRelease', 'RC_VERIFIED.txt'):
    assert gate in text, gate
assert text.index('VerifyAabSigner.java') < text.index('RC_VERIFIED.txt')
assert text.index('verify_release_apk.py') < text.index('RC_VERIFIED.txt')
workflow = (root/'docs/release/iumrah-play-release.yml').read_text()
for gate in ('workflow_dispatch:', 'setup-android@v4.0.4', 'bundletool-all-1.18.2.jar', 'test_release_v10.py', 'test -s artifacts/play-release/RC_VERIFIED.txt', 'if: ${{ always() }}', 'bash scripts/build-play-release.sh "$PLAY_VERSION_CODE" "$PLAY_VERSION_NAME"'):
    assert gate in workflow, gate
assert "'${{ inputs.version_code }}'" not in workflow
assert (root/'scripts/qa/VerifyAabSigner.java').is_file()
print('V10 release source gates passed (signed APK/AAB still require CI).')
