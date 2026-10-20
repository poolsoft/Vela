"""Verify native submenu dispatch on a visible small music panel without clearing app data.

Pass the small player's three-dot menu coordinates with --anchor X Y. Requires a
 debuggable installed APK to verify the saved color and two-color preference.
"""
import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial', required=True)
parser.add_argument('--anchor', nargs=2, type=int, required=True)
parser.add_argument('--output', type=Path, default=Path('.logs/native-submenu-regression'))
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)


def adb(*parts):
    return subprocess.check_output([args.adb, '-s', args.serial, *map(str, parts)], timeout=45)


def prefs():
    raw = adb('shell', 'run-as', 'app.vela', 'cat', 'shared_prefs/small_music_panel.xml')
    return {item.attrib['name']: item.attrib.get('value', item.text) for item in ET.fromstring(raw)}


def nodes():
    adb('shell', 'rm', '-f', '/sdcard/vela-popup.xml')
    result = adb('shell', 'uiautomator', 'dump', '/sdcard/vela-popup.xml')
    assert b'ERROR' not in result, 'UI hierarchy did not become idle'
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/vela-popup.xml')).iter('node'))


def tap_text(*labels):
    for attempt in range(4):
        visible = nodes()
        for node in visible:
            if any(label.casefold() in node.attrib.get('text', '').casefold() for label in labels):
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
                if y2 > y1:
                    adb('shell', 'input', '-d', '0', 'tap', (x1 + x2) // 2, (y1 + y2) // 2)
                    time.sleep(.5)
                    return
        scroll = next((n for n in visible if n.attrib.get('scrollable') == 'true'), None)
        assert scroll is not None, f'Missing menu item: {labels}'
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', scroll.attrib['bounds']))
        adb('shell', 'input', '-d', '0', 'swipe', (x1+x2)//2, y2-12, (x1+x2)//2, y1+12, 350)
    raise AssertionError(f'Menu item not visible after scrolling: {labels}')


def open_colors():
    adb('shell', 'input', '-d', '0', 'tap', *args.anchor)
    time.sleep(.3)
    tap_text('Saat rengi', 'Clock color')


pid = adb('shell', 'pidof', 'app.vela').strip()
assert pid, 'Vela must already be running with the small player visible'
original = prefs()
(args.output / "original-preferences.txt").write_text(repr(original), encoding="utf-8")
colors = [('Turkuaz', 'Turquoise'), ('Tema rengi', 'Theme color'), ('Kehribar', 'Amber'),
          ('Transparan', 'Transparent'), ('Beyaz', 'White'), ('Özel renkler', 'Custom colors'),
          ('Yumuşak pastel', 'Soft pastel'), ('Panel arka planına', 'Panel background')]
try:
    for value in (4, 7):
        open_colors()
        tap_text(*colors[value])
        time.sleep(.5)
        assert prefs().get('clock_color') == str(value), f'Wrong submenu action for color {value}'
        print(f'PASS: submenu color {value}', flush=True)
        assert adb('shell', 'pidof', 'app.vela').strip() == pid, 'Launcher process restarted'
        (args.output / f'color-{value}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    before = prefs().get('clock_two_colors', 'true')
    for _ in range(2):
        open_colors()
        tap_text('Çift renk', 'Two colors')
        time.sleep(.5)
    assert prefs().get('clock_two_colors', 'true') == before, 'Two-color toggle did not round trip'
    print('PASS: two-color toggle round trip', flush=True)
    open_colors()
    tap_text(*colors[5])
    tap_text('Vazgeç', 'Cancel')
    assert adb('shell', 'pidof', 'app.vela').strip() == pid, 'Launcher process restarted'
    print('PASS: larger child menu, white/background dispatch, two-color toggle and custom dialog')
finally:
    # Restore the original palette; custom swatches have not been modified.
    if adb('shell', 'pidof', 'app.vela').strip() == pid:
        open_colors()
        tap_text(*colors[int(original.get('clock_color', '6'))])
        if original.get('clock_color') == '5':
            tap_text('Kaydet', 'Save')
