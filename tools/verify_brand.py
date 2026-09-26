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
    check_one_location_flag()
    check_refresh_button_clears_the_connect_fab()
    check_refresh_button_actually_refreshes()
    check_release_workflow()
    print('NiNG app ID, internal namespace, updater, locale names, drawer and adaptive icons: OK')
    print('No user-visible PattNG string and every fork feature file is present: OK')
    print('One location flag, taken from the main server verdict: OK')
    print('Refresh button clears the connect FAB: OK')
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

def check_one_location_flag():
    """The row must carry exactly one location flag, for the main server.

    It used to carry two: one guessed from the profile name and one from a separate address
    lookup. The name hint described the label rather than the server reached, so the two could
    disagree and both were rendered. The verdict response already carries the country of the
    address it was asked about, which is the main server, and that is now the only source.
    """
    models = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainServerRowModels.kt').read_text('utf-8')
    assert 'labelCountryCode' not in models, (
        'a name-derived labelCountryCode is back; the location flag must describe the server')
    pager = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainServerPager.kt').read_text('utf-8')
    assert 'row.labelCountryCode' not in pager, 'the row renders a second, name-based location flag'
    assert 'row.serverCountryCode' in pager, 'the row no longer renders the server location flag'
    rows = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainCountryRows.kt').read_text('utf-8')
    assert 'country: String? = null' in rows, (
        'applyServerFlag no longer carries the country from the verdict')
    view_model = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt').read_text('utf-8')
    assert 'verdict.countryCode' in view_model, (
        'the verdict country is discarded again, leaving the location flag empty')

def check_refresh_button_clears_the_connect_fab():
    """The refresh button sat under the connect FAB, so its taps started the service instead.

    A FAB is 56dp wide with 24dp of end inset, drawn on top of this bar, so the button needs a
    larger end padding than the bar's own inset or the two overlap and the button is dead.
    """
    bar = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainBottomBar.kt').read_text('utf-8')
    # The button may carry a comment or an extra argument between onClick and modifier, so this
    # reads the padding from the button block rather than assuming an exact argument order.
    block = re.search(r'SmallFloatingActionButton\((.*?)\n\s*\) \{', bar, re.S)
    assert block, 'the refresh button is gone'
    padding = re.search(r'Modifier\.padding\(end\s*=\s*([\d.]+)dp\)', block.group(1))
    assert padding, 'the refresh button lost its end padding; it is drawn under the connect FAB again'
    assert float(padding.group(1)) >= 96, (
        f'refresh button end padding is {padding.group(1)}dp, under the 56dp FAB plus its 24dp inset')

def check_refresh_button_actually_refreshes():
    """A tap used to be swallowed, so the button looked dead even though the click arrived.

    The batch was restarted on every tap while each lookup is rate limited, so a full page never
    finished and no badge ever changed. The pass must now be able to run to completion, a tap that
    arrives mid-pass must be honoured afterwards, and the button must show that it is busy.
    """
    batch = (APP / 'src/main/java/com/v2ray/ang/ui/main/FlagBatch.kt')
    assert batch.exists(), 'the flag batch runner is gone; a tap can cancel a pass again'
    view_model = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt').read_text('utf-8')
    assert 'runFlagBatch(' in view_model, 'the flag batch no longer runs through the completion helper'
    assert 'isNewerRequested' in view_model, 'a mid-pass tap is dropped instead of queued'
    bar = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainBottomBar.kt').read_text('utf-8')
    assert 'if (!isRefreshingFlags) onAction(MainAction.RefreshFlags)' in bar, (
        'a tap during a pass is no longer guarded, so it queues a duplicate pass')
    assert 'if (isRefreshingFlags) {' in bar, (
        'the refresh button gives no feedback while a pass is running')
    test = (ROOT / 'V2rayNG/app/src/test/java/com/v2ray/ang/ui/main/FlagRefreshBatchTest.kt')
    assert test.exists(), 'the regression test for a swallowed tap is gone'

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
    # The build job owns the pinned toolchain versions; a job that compiles native code or
    # reads those scripts needs them in its own env, because job envs do not leak sideways.
    toolchain = jobs.get('build', {}).get('env', {})
    required_env = ('RUST_TOOLCHAIN', 'NDK_HOME', 'GO_VERSION')
    for name, job in jobs.items():
        # Secrets appear both in env: and inline in run:, so both have to be scanned.
        blob = '\n'.join(
            [str(s.get('run', '')) + '\n' + str(s.get('env', {})) for s in job.get('steps', [])]
        ) + '\n' + str(job.get('env', {}))
        steps_blob = '\n'.join(str(s.get('with', {})) for s in job.get('steps', []))
        # A variable set through GITHUB_ENV earlier in the same job is defined, even though it is
        # absent from env:. Only variables the job never sets anywhere count as missing.
        sets_in_run = '\n'.join(str(s.get('run', '')) for s in job.get('steps', []))
        for key in required_env:
            referenced = f'env.{key}' in steps_blob or f'env.{key}' in blob
            defined = key in (job.get('env') or {}) or f'{key}=' in sets_in_run
            assert not referenced or defined, (
                f"job '{name}' uses ${{{{ env.{key} }}}} but never sets it; "
                'a job env does not inherit another job env')
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
