"""Check the packaged car launcher contract, after manifest merging and shrinking."""
import argparse
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--aapt', required=True)
parser.add_argument('apks', nargs='+')
args = parser.parse_args()
for apk in args.apks:
    xml = subprocess.check_output([args.aapt, 'dump', 'xmltree', apk, 'AndroidManifest.xml'], text=True, encoding='utf-8', errors='replace')
    activities = re.findall(r'E: activity \([^\n]+\)\n((?:\s+A:[^\n]+\n)+)', xml)
    main = [attributes for attributes in activities if re.search(r'android:name[^=]*="(?:app\.vela)?\.MainActivity"', attributes)]
    assert len(main) == 1, f'{apk}: expected one MainActivity declaration'
    assert re.search(r'android:launchMode[^=]*=\(type 0x10\)0x2', main[0]), f'{apk}: launcher must be singleTask'
    assert re.search(r'android:supportsPictureInPicture[^=]*=\(type 0x12\)0x0', main[0]), f'{apk}: car launcher must not support PiP'
    print(f'PASS: packaged launcher is singleTask with PiP disabled: {apk}')

    blocks = re.findall(r'(?m)^( +)E: activity \([^\n]+\)\n(.*?)(?=^\1E: |\Z)', xml, re.S | re.M)
    home_owners = [block for _, block in blocks if 'android.intent.category.HOME' in block]
    assert len(home_owners) == 1 and 'app.vela.carlauncher.ui.CarLauncherEntryActivity' in home_owners[0], f'{apk}: HOME must belong only to the forwarding entry'
