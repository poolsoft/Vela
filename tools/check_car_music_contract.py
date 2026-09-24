from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/car/res'
ANDROID = '{http://schemas.android.com/apk/res/android}'

# Checks the actual XML/view binding contract without compiling an Android module.
def main():
    layout = ET.parse(RES / 'layout/fragment_music_player.xml').getroot()
    ids = {}
    for element in layout.iter():
        identity = element.get(ANDROID + 'id', '').split('/')[-1]
        if identity:
            assert identity not in ids, f'Duplicate player view ID: {identity}'
            ids[identity] = element
    assert ids['now_playing_center_panel'].get(ANDROID + 'visibility') != 'gone'
    assert ids['now_playing_center_art_card'].get(ANDROID + 'visibility') != 'gone'
    assert ids['track_list_panel'].get(ANDROID + 'visibility') == 'gone'
    for name in ('tab_queue', 'tab_all_tracks', 'tab_folders', 'tab_playlists'):
        assert ids[name].tag == 'TextView', name
    item_ids = {e.get(ANDROID + 'id', '').split('/')[-1] for e in ET.parse(RES / 'layout/item_music_track.xml').iter()}
    bound_ids = set()
    for name in ('CarMusicPlayerHost.kt', 'MusicLibraryController.kt'):
        text = (ROOT / 'app/src/car/java/app/vela/carlauncher/ui' / name).read_text(encoding='utf-8')
        bound_ids.update(re.findall(r'findViewById(?:<[^>]+>)?\(R\.id\.(\w+)\)', text))
    assert bound_ids <= ids.keys() | item_ids, 'Missing bound views: ' + str(bound_ids - ids.keys() - item_ids)
    for folder in ('values', 'values-tr'):
        names = [e.get('name') for e in ET.parse(RES / folder / 'strings_car.xml').getroot() if e.get('name')]
        assert len(names) == len(set(names)), f'Duplicate resource in {folder}'
    manifest = ET.parse(ROOT / 'app/src/car/AndroidManifest.xml').getroot()
    home = next(e for e in manifest.iter('activity-alias') if e.get(ANDROID + 'name') == '.CarHome')
    assert home.get(ANDROID + 'targetActivity') == '.MainActivity'
    assert any(e.get(ANDROID + 'name') == 'android.intent.category.HOME' for e in home.iter('category'))
    print(f'PASS: {len(bound_ids)} player bindings, initial XML visibility, resource names and HOME alias')
    print('Static contract only; device input, audio focus and vendor protocols still require device acceptance.')

if __name__ == '__main__':
    main()