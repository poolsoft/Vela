"""Exercise launcher return paths on an already installed, running Vela emulator.

No data is cleared, no navigation is stopped and no network settings are changed.
Use --serial to select the intended emulator. Screenshots can contain private map data.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, default=Path('.logs/task-return-regression'))
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)


def adb(*parts):
    return subprocess.check_output([args.adb, '-s', args.serial, *parts], timeout=45)


def snapshot(label):
    dump = adb('shell', 'dumpsys', 'activity', 'activities').decode('utf-8', errors='replace')
    (args.output / (label + '.txt')).write_text(dump, encoding='utf-8')
    (args.output / (label + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    records = set(re.findall(r'Hist #\d+: ActivityRecord\{([^ ]+) u\d+ app\.vela/\.MainActivity t(\d+)\}', dump))
    assert len(records) == 1, f'{label}: expected one MainActivity, found {records}'
    task = next(iter(records))[1]
    assert re.search(r'mResumedActivity: ActivityRecord\{[^\n]+app\.vela/\.MainActivity t' + task + r'\}', dump), f'{label}: owner not resumed'
    return task


# Explicit normal-entry delivery also cleans up a stale restored record from the old APK.
adb('shell', 'am', 'start', '-W', '-n', 'app.vela/.MainActivity', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER')
time.sleep(3)
owner = snapshot('initial')
pid = adb('shell', 'pidof', 'app.vela').strip()
for i in range(3):
    adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', '-p', 'com.android.chrome')
    time.sleep(2)
    if i == 0:
        adb('shell', 'input', 'keyevent', '3')
    elif i == 1:
        adb('shell', 'am', 'start', '-W', '-n', 'app.vela/.MainActivity')
    else:
        component = adb('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', '-p', 'app.vela').decode().strip().splitlines()[-1]
        assert component.startswith('app.vela/'), component
        adb('shell', 'am', 'start', '-W', '-n', component, '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER')
    time.sleep(3)
    assert snapshot('return-' + str(i)) == owner, 'Owner task changed'
    assert adb('shell', 'pidof', 'app.vela').strip() == pid, 'Launcher process restarted'

(args.output / 'summary.json').write_text(json.dumps({'task': owner, 'pid': pid.decode(), 'return_paths': ['HOME', 'notification-style explicit entry', 'launcher alias'], 'result': 'passed'}, indent=2), encoding='utf-8')
print('PASS: same process and one owner task across all three return paths')
