#!/usr/bin/env python3
"""Offline screenshot diff; requires Pillow and two sets of normalized PNG captures.

No screenshots are included in the source repository. This tool is intentionally
manual; it NEVER claims visual parity without matching real-device captures.
Usage:
  python3 scripts/qa/compare_ios_android_screens.py --ios captures/ios --android captures/android --out captures/diff
Capture identical CONTENT viewport sizes and filenames on both platforms.
"""
import argparse
import json
import sys
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description='Compare actual iOS / Android captures')
    p.add_argument('--ios', type=Path, required=True)
    p.add_argument('--android', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--max-mae', type=float, default=8, help='Mean RGB error on 0–255 scale, not a guarantee of UX parity')
    p.add_argument('--exact', action='store_true', help='Fail for any differing RGB pixel; no tolerance or resizing')
    args = p.parse_args()
    if args.max_mae < 0:
        p.error('--max-mae must be nonnegative')
    try:
        from PIL import Image, ImageChops, ImageEnhance, ImageStat
    except ImportError:
        raise SystemExit('Missing dependency Pillow; run pip install pillow to use screenshot comparison')
    ios = {f.relative_to(args.ios): f for f in args.ios.rglob('*.png')}
    android = {f.relative_to(args.android): f for f in args.android.rglob('*.png')}
    if not ios or not android:
        raise SystemExit('No PNG captures found in both directories; no pixel comparison was performed')
    if set(ios) != set(android):
        missing_a = sorted(str(f) for f in set(ios)-set(android))
        missing_i = sorted(str(f) for f in set(android)-set(ios))
        raise SystemExit(f'Missing Android captures: {missing_a}; missing iOS captures: {missing_i}')
    args.out.mkdir(parents=True, exist_ok=True)
    results = []
    for relative in sorted(ios):
        with Image.open(ios[relative]) as source, Image.open(android[relative]) as target:
            i, a = source.convert('RGB'), target.convert('RGB')
            if i.size != a.size:
                raise SystemExit(f'Size mismatch for {relative}: iOS {i.size}, Android {a.size}. '
                                 'Normalize viewport and recapture; automatic scaling conceals layout errors.')
            diff = ImageChops.difference(i,a)
            means = ImageStat.Stat(diff).mean
            mae = sum(means)/3
            total = i.width * i.height
            changed = 0
            mismatched = 0
            for color in diff.getdata():
                peak = max(color)
                changed += peak > 0
                mismatched += peak > 20
            ratio = mismatched / total
            identical = changed == 0
            passed = identical if args.exact else mae <= args.max_mae
            preview = args.out / relative
            preview.parent.mkdir(parents=True, exist_ok=True)
            ImageEnhance.Contrast(diff).enhance(3.5).save(preview)
            results.append({'screen':str(relative),'size':list(i.size),
                            'mae_rgb':round(mae,3), 'fraction_pixels_over_20':round(ratio,5),
                            'changed_pixels':changed, 'total_pixels':total, 'exact_rgb_match':identical,
                            'within_mae_threshold':mae <= args.max_mae, 'passed':passed})
    report = args.out/'comparison.json'
    report.write_text(json.dumps({'mode':'exact_rgb' if args.exact else 'mae_tolerance', 'threshold_mae':args.max_mae,'results':results},indent=2,ensure_ascii=False)+'\n')
    for row in results:
        print(f"{'PASS' if row['passed'] else 'REVIEW'} {row['screen']}: MAE={row['mae_rgb']}, changed={100*row['fraction_pixels_over_20']:.1f}%")
    print(f'Wrote {report}; pixel scores cannot prove behavior/API parity.')
    if not all(r['passed'] for r in results):
        sys.exit(1)

if __name__ == '__main__':
    main()
