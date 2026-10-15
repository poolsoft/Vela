"""Emulator UI smoke check: opening/closing the drawer must not rescan packages.

Run against the NEW diagnostic APK, with no installs/uninstalls occurring during the check.
This restarts Vela but never clears its data. Requires adb and visible launcher dock.
"""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    base = [args.adb, '-s', args.serial]

    def shell(*words):
        return subprocess.check_output(base + ['shell', *words], text=True)

    def tap(resource):
        for _ in range(8):
            shell('uiautomator', 'dump', '/data/local/tmp/vela-drawer-check.xml')
            root = ET.fromstring(shell('cat', '/data/local/tmp/vela-drawer-check.xml'))
            node = next((n for n in root.iter('node')
                         if n.get('resource-id') == 'app.vela:id/' + resource), None)
            if node is not None:
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                shell('input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
                return
            time.sleep(0.5)
        raise AssertionError('Control not found: ' + resource)

    shell('am', 'force-stop', 'app.vela')
    subprocess.check_call(base + ['logcat', '-c'])
    shell('am', 'start', '-n', 'app.vela/.MainActivity')
    time.sleep(12)
    for _ in range(3):
        tap('btn_app_list')
        time.sleep(2)
        tap('btn_close_drawer')
    logs = subprocess.check_output(base + ['logcat', '-d', '-s', 'CarAppManager:I', '*:S'], text=True)
    scans = logs.count('apps scan start generation=')
    assert scans == 1, f'Expected one scan across three openings, found {scans}'
    print('PASS: three drawer openings, one package scan')


if __name__ == '__main__':
    main()
