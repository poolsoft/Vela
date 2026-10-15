"""ADB integration check for a diagnostic APK; never removes application data.

Cold start: python scripts/check_tts_process.py --serial emulator-5554 --cold-start
Crash containment: select Vela voice, press Test, then run with --crash-tts.
Requires adb root (emulator) or debuggable run-as for the crash check.
"""
import argparse
import json
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--cold-start', action='store_true')
    mode.add_argument('--crash-tts', action='store_true')
    args = parser.parse_args()

    def shell(*words, check=True):
        return subprocess.run([args.adb, '-s', args.serial, 'shell', *words],
                              check=check, capture_output=True, text=True).stdout.strip()

    def pid(name):
        return shell('pidof', name, check=False)

    if args.cold_start:
        shell('am', 'force-stop', 'app.vela')
        shell('am', 'start', '-n', 'app.vela/.MainActivity')
        time.sleep(15)
        launcher = pid('app.vela')
        assert launcher, 'Launcher did not start'
        assert not pid('app.vela:voice_tts'), 'TTS process started without a speech request'
        print(json.dumps({'cold_start': 'passed', 'launcher_pid': launcher}))
    else:
        launcher, child = pid('app.vela'), pid('app.vela:voice_tts')
        assert launcher and child, 'First select an installed Vela voice and press Test'
        assert launcher != child, 'Native TTS must have a separate PID'
        shell('run-as', 'app.vela', 'kill', '-9', child)
        time.sleep(5)
        assert pid('app.vela') == launcher, 'Launcher process changed after TTS process death'
        assert not pid('app.vela:voice_tts'), 'TTS restarted automatically after failure'
        prefs = shell('run-as', 'app.vela', 'cat', 'shared_prefs/vela_settings.xml')
        engine = next((node.text for node in ET.fromstring(prefs)
                       if node.get('name') == 'voice_engine'), None)
        assert engine == 'com.google.android.tts', f'Expected Google fallback, got {engine}'
        print(json.dumps({'crash_containment': 'passed', 'launcher_pid': launcher, 'engine': engine}))


if __name__ == '__main__':
    main()
