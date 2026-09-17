"""Static NiNG identity checks; Android compilation remains in CI."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'V2rayNG/app'

def verify():
    gradle = (APP / 'build.gradle.kts').read_text(encoding='utf-8')
    assert 'applicationId = "com.herbertkara.ning"' in gradle
    assert 'namespace = "com.v2ray.ang"' in gradle
    config = (APP / 'src/main/java/com/v2ray/ang/AppConfig.kt').read_text(encoding='utf-8')
    assert 'herbert-kara/NiNG' in config and 'herbertkara/NiNG' not in config
    for file in (APP / 'src').glob('*/res/values*/strings.xml'):
        for item in ET.parse(file).getroot():
            if item.get('name') == 'app_name':
                assert 'NiNG' in (item.text or ''), str(file)
    icon = (APP / 'src/main/res/drawable/ic_ning_monochrome.xml')
    assert icon.exists(), 'NiNG monochrome icon missing'
    ET.parse(icon)
    for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        text = (APP / 'src/main/res/mipmap-anydpi-v26' / name).read_text(encoding='utf-8')
        assert '@drawable/ic_ning_monochrome' in text
    drawer = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainDrawer.kt').read_text(encoding='utf-8')
    assert 'R.drawable.ic_ning_logo' in drawer
    print('NiNG app ID, internal namespace, updater, locale names, drawer and adaptive icons: OK')

if __name__ == '__main__':
    verify()
