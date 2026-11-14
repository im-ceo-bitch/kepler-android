"""Local release gates with real JDK signatures; no Android SDK or secrets needed."""
import hashlib
import importlib.util
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]

def load(name):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

inputs = load('release_input')
manifest = load('verify_play_manifest')
apk = load('verify_release_apk')

class ReleaseInputTests(unittest.TestCase):
    def test_valid(self):
        for code, name in [('15', '2.0.3'), ('16', '2.1.0-rc.1'), ('2100000000', 'X')]:
            with self.subTest(code=code): inputs.validate(code, name)

    def test_bad_codes(self):
        for code in ['14', '0', '015', '-1', '2100000001', '1e3', '15\n', '', '999999999999999999999']:
            with self.subTest(code=code), self.assertRaises(ValueError): inputs.validate(code, '2.0.3')

    def test_bad_names(self):
        for name in ['', '../escape', 'x/y', 'x;echo', "x'", 'x\n', 'a'*81, '-a']:
            with self.subTest(name=name), self.assertRaises(ValueError): inputs.validate('15', name)

class ManifestTests(unittest.TestCase):
    def root(self):
        return ET.fromstring('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.iumrah.app" android:versionCode="16" android:versionName="2.1.0"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/><application android:allowBackup="false" android:usesCleartextTraffic="false"/></manifest>''')

    def test_valid(self): manifest.verify(self.root(), 16, '2.1.0')

    def test_identity(self):
        with self.assertRaises(ValueError): manifest.verify(self.root(), 17, '2.1.0')
        with self.assertRaises(ValueError): manifest.verify(self.root(), 16, '2.2.0')
        root = self.root(); root.set('package', 'com.iumrah.beta')
        with self.assertRaises(ValueError): manifest.verify(root, 16, '2.1.0')

    def test_security_flags(self):
        for key in ['allowBackup', 'usesCleartextTraffic', 'debuggable', 'testOnly']:
            root = self.root(); root.find('application').set(manifest.ANDROID+key, 'true')
            with self.subTest(key=key), self.assertRaises(ValueError): manifest.verify(root, 16)
        for key in ['allowBackup', 'usesCleartextTraffic']:
            root = self.root(); del root.find('application').attrib[manifest.ANDROID+key]
            with self.subTest(key=key), self.assertRaises(ValueError): manifest.verify(root, 16)

    def test_permissions(self):
        for permission in ['android.permission.READ_SMS', 'com.android.vending.BILLING']:
            root = self.root(); ET.SubElement(root, 'uses-permission', {manifest.ANDROID+'name': permission})
            with self.subTest(permission=permission), self.assertRaises(ValueError): manifest.verify(root, 16)

class ApkReportTests(unittest.TestCase):
    cert = 'A'*64
    signature = 'Verifies\nSigner #1 certificate SHA-256 digest: ' + cert.lower() + '\n'
    badging = "package: name='com.iumrah.app' versionCode='16' versionName='2.1.0'\n"

    def test_valid(self): apk.verify(self.signature, self.badging, self.cert, '16', '2.1.0')

    def test_bad_signature(self):
        for sig in ['', self.signature.replace(self.cert.lower(), 'b'*64), self.signature + self.signature.replace('#1', '#2')]:
            with self.subTest(sig=sig), self.assertRaises(ValueError): apk.verify(sig, self.badging, self.cert, '16', '2.1.0')

    def test_badging_mismatch(self):
        for badging in [self.badging.replace('com.iumrah.app', 'com.iumrah.beta'), self.badging.replace("'16'", "'17'"), self.badging.replace('2.1.0', '2.2.0'), self.badging + 'application-debuggable\n', '']:
            with self.subTest(badging=badging), self.assertRaises(ValueError): apk.verify(self.signature, badging, self.cert, '16', '2.1.0')

class SignedArtifactTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(); cls.addClassCleanup(cls.tmp.cleanup)
        cls.root = Path(cls.tmp.name); cls.key = cls.root / 'fixture.p12'
        subprocess.run(['keytool', '-genkeypair', '-alias', 'fixture', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '30', '-dname', 'CN=Local QA fixture', '-storetype', 'PKCS12', '-keystore', str(cls.key), '-storepass', 'fixture-only'], check=True, capture_output=True)
        cert = subprocess.check_output(['keytool', '-exportcert', '-alias', 'fixture', '-keystore', str(cls.key), '-storepass', 'fixture-only'])
        cls.digest = hashlib.sha256(cert).hexdigest().upper()
        cls.unsigned = cls.root / 'unsigned.aab'; cls.signed = cls.root / 'signed.aab'
        with zipfile.ZipFile(cls.unsigned, 'w') as archive:
            archive.writestr('base/manifest/AndroidManifest.xml', b'fixture only')
            archive.writestr('base/dex/classes.dex', b'not an APK')
        shutil.copyfile(cls.unsigned, cls.signed)
        subprocess.run(['jarsigner', '-keystore', str(cls.key), '-storepass', 'fixture-only', str(cls.signed), 'fixture'], check=True, capture_output=True)

    def verify(self, path, digest=None):
        return subprocess.run(['java', str(SCRIPTS/'qa/VerifyAabSigner.java'), str(path), digest or self.digest], capture_output=True, text=True)

    def test_signed(self):
        result = self.verify(self.signed)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('entries verified: 2', result.stdout)

    def test_unsigned(self): self.assertNotEqual(self.verify(self.unsigned).returncode, 0)

    def test_wrong_key(self): self.assertNotEqual(self.verify(self.signed, '0'*64).returncode, 0)

    def test_added_unsigned_entry(self):
        target = self.root/'added.aab'; shutil.copyfile(self.signed, target)
        with zipfile.ZipFile(target, 'a') as archive: archive.writestr('base/assets/added', b'unsigned')
        self.assertNotEqual(self.verify(target).returncode, 0)

    def test_tampered_payload(self):
        target = self.root/'tampered.aab'
        with zipfile.ZipFile(self.signed) as source, zipfile.ZipFile(target, 'w') as out:
            for entry in source.infolist():
                data = source.read(entry)
                out.writestr(entry, b'changed' if entry.filename == 'base/dex/classes.dex' else data)
        self.assertNotEqual(self.verify(target).returncode, 0)

if __name__ == '__main__': unittest.main()
