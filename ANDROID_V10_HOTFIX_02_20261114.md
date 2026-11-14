# V10 Hotfix 02 — repeat Kotlin failure

The new debug log (118113207775) checked out 40f88e2aecd4f3537e2f7bbdc0b98bedbbbab807 and repeated all 18 errors from 118094182122 (89c2bc11febd66a5ac2d2f6b75900e399d7eb4f9). The fixed four files already exist in Hotfix 01; the new checkout still compiled the old code. Exact remote application failure is unconfirmed.

This cumulative archive preserves Hotfix 01 and adds an eight-condition source guard to verify-stage10.sh. Filename sorts after prior 20261114 patches. After uploading, check successful apply-update and Commit applied files, then manually start a fresh APK Test Build on main. Full Gradle build not locally verified.

Release certificate: PLAY_UPLOAD_CERT_SHA256 must be in Actions Variables if workflow uses vars.PLAY_UPLOAD_CERT_SHA256. Do not reset the upload key.
