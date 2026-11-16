"""Synthetic tests of strict RGB comparison; not device screenshot results."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
try:
    from PIL import Image
except ImportError:
    Image = None

SCRIPT = Path(__file__).resolve().parents[1] / 'qa/compare_ios_android_screens.py'

@unittest.skipIf(Image is None, "Pillow is required for screenshot comparison")
class TestExactComparison(unittest.TestCase):
    def test_exact_pixels_and_single_channel_difference(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); ios=root/'ios'; android=root/'android'; out=root/'out'
            ios.mkdir(); android.mkdir()
            base=Image.new('RGB',(8,8),(22,33,44)); base.save(ios/'screen.png'); base.save(android/'screen.png')
            cmd=[sys.executable,str(SCRIPT),'--ios',str(ios),'--android',str(android),'--out',str(out),'--exact']
            self.assertEqual(subprocess.run(cmd,capture_output=True).returncode,0)
            row=json.loads((out/'comparison.json').read_text())['results'][0]
            self.assertTrue(row['exact_rgb_match']); self.assertEqual(row['changed_pixels'],0)
            base.putpixel((0,0),(23,33,44)); base.save(android/'screen.png')
            self.assertEqual(subprocess.run(cmd,capture_output=True).returncode,1)
            row=json.loads((out/'comparison.json').read_text())['results'][0]
            self.assertFalse(row['exact_rgb_match']); self.assertEqual(row['changed_pixels'],1)
            self.assertTrue(row['within_mae_threshold']); self.assertFalse(row['passed'])
            # Tolerant diagnostics remain available, but must never pass strict mode.
            self.assertEqual(subprocess.run(cmd[:-1],capture_output=True).returncode,0)
            Image.new('RGB',(9,8)).save(android/'screen.png')
            self.assertNotEqual(subprocess.run(cmd,capture_output=True).returncode,0)
            (android/'screen.png').unlink()
            self.assertNotEqual(subprocess.run(cmd,capture_output=True).returncode,0)

if __name__ == '__main__': unittest.main()
