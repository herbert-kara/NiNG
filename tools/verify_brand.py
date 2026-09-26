"""Static NiNG identity checks; Android compilation remains in CI."""
from pathlib import Path
import re
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
    check_no_user_visible_upstream_brand()
    check_fork_feature_files()
    check_release_workflow()
    print('NiNG app ID, internal namespace, updater, locale names, drawer and adaptive icons: OK')
    print('No user-visible PattNG string and every fork feature file is present: OK')
    print('Release workflow structure (checkout, secrets, artifact graph): OK')

# Upstream adds new user-visible strings with ITS brand in them. app_name was checked before,
# but a string added after that check arrives with the upstream name and is only noticed on a
# device. Every user-visible string value is therefore checked, in every locale.
USER_VISIBLE = re.compile(
    r'PattNG|pattng', re.IGNORECASE)

def check_no_user_visible_upstream_brand():
    # Kotlin/Gradle comments may legitimately mention the upstream project by name, so only
    # the resource values a user can actually read are checked.
    for file in (APP / 'src').glob('*/res/values*/strings.xml'):
        root = ET.parse(file).getroot()
        for item in root:
            if item.tag != 'string':
                continue
            value = ''.join(item.itertext())
            assert not USER_VISIBLE.search(value), (
                f'{file}: string {item.get("name")} shows the upstream brand: {value!r}')

def check_fork_feature_files():
    # An upstream merge that resolved "cleanly" can still drop a file the fork owns, because
    # the file is new on one side only. Its absence must fail the guard, not the release.
    for name in (
        'src/main/java/com/v2ray/ang/handler/ProfileRisk.kt',
        'src/main/java/com/v2ray/ang/handler/ServerFlaggedLookup.kt',
        'src/main/java/com/v2ray/ang/handler/ProfileCountry.kt',
        'src/main/java/com/v2ray/ang/handler/ServerCountryLookup.kt',
        'src/main/java/com/v2ray/ang/ui/main/RiskBadge.kt',
    ):
        assert (APP / name).exists(), f'fork feature file lost in merge: {name}'
    for name in ('risk_strings.xml', 'flag_strings.xml'):
        assert (APP / 'src/main/res/values' / name).exists(), f'fork string file lost: {name}'

def check_release_workflow():
    """Catch the release-pipeline mistakes that compile fine and fail only at run time.

    Three separate upstream merges broke the build this way, all invisible to CI because each
    one compiles and lints cleanly:
      1. merge markers left inside a workflow, which hide its triggers from GitHub;
      2. an upstream signing path referencing secrets this fork never had;
      3. a job that reads the working tree with no checkout of its own, and a job that
         downloads an artifact no job it depends on uploads.
    """
    import re
    import yaml
    path = ROOT / '.github/workflows/build.yml'
    text = path.read_text(encoding='utf-8')
    for marker in ('<<<<<<<', '>>>>>>>'):
        assert marker not in text, f'merge markers left in {path.name}'
    doc = yaml.safe_load(text)
    assert True in doc or 'on' in doc, 'build.yml lost its triggers'
    jobs = doc['jobs']
    own_secrets = ('NING_',)
    for name, job in jobs.items():
        # Secrets appear both in env: and inline in run:, so both have to be scanned.
        blob = '\n'.join(
            [str(s.get('run', '')) + '\n' + str(s.get('env', {})) for s in job.get('steps', [])]
        ) + '\n' + str(job.get('env', {}))
        uses = [str(s.get('uses', '')) for s in job.get('steps', [])]
        reads_tree = 'git -C ' in blob or './gradlew' in blob or 'V2rayNG' in blob
        assert reads_tree is False or any(u.startswith('actions/checkout') for u in uses), (
            f"job '{name}' uses the working tree but has no actions/checkout; "
            'jobs are independent workspaces')
        for secret in set(re.findall(r'secrets\.([A-Z_0-9]+)', blob)):
            assert any(secret.startswith(p) for p in own_secrets), (
                f"job '{name}' references secrets.{secret}, which this fork does not have")
        deps = job.get('needs')
        deps = [deps] if isinstance(deps, str) else (deps or [])
        uploaded = set()
        for dep in deps:
            for s in jobs.get(dep, {}).get('steps', []):
                if 'upload-artifact' in str(s.get('uses', '')):
                    artifact = (s.get('with') or {}).get('name')
                    if artifact:
                        uploaded.add(artifact)
        for s in job.get('steps', []):
            if 'download-artifact' in str(s.get('uses', '')):
                want = (s.get('with') or {}).get('name')
                assert not want or want in uploaded, (
                    f"job '{name}' downloads '{want}' but none of {deps} uploads it")

if __name__ == '__main__':
    verify()
