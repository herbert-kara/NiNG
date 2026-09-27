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
    check_bottom_bar_text_stays_inside_the_bar()
    check_refresh_button_actually_refreshes()
    check_the_flag_path_is_covered_by_a_real_test()
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
    """The refresh button was drawn under the connect FAB, and ended up below it on screen.

    Both controls now have to be siblings in one Row. Offsets and a second navigation-bar inset
    are what put them in different places: the bar was already inset for the navigation bar and
    the FAB carried its own inset, so the two drifted apart vertically and the refresh button
    rendered below and left of connect instead of beside it.
    """
    bar = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainBottomBar.kt').read_text('utf-8')
    # The Row that holds the two controls: the one carrying a spacedBy arrangement and TopEnd.
    rows = re.findall(r'Row\(\s*modifier = Modifier(.*?)\n\s*\) \{', bar, re.S)
    button_row = next((r for r in rows if 'TopEnd' in r and 'spacedBy' in r), None)
    assert button_row is not None, 'the button row is gone, so connect and refresh can drift apart again'
    body = bar[bar.index(button_row):]
    body = body[:body.index('\n        }\n    }')] if '\n        }\n    }' in body else body
    for control in ('FloatingActionButton(', 'SmallFloatingActionButton('):
        assert control in body, f'{control} is no longer a sibling of the other button'
    assert button_row.count('navigationBarsPadding') == 1, (
        'the button row carries its own navigationBarsPadding on top of the bar, so the two '
        'buttons no longer share a baseline')
    assert not re.search(r'offset\s*\(\s*y\s*=', bar), (
        'a negative y offset is back; the buttons are positioned by offset instead of by layout')
    assert bar.count('SmallFloatingActionButton(') == 1, (
        'the refresh control is duplicated or missing; a tap could land on the wrong one')

def check_bottom_bar_text_stays_inside_the_bar():
    """The status line wrapped to three lines and spilled out over the list.

    A fixed 64dp row height forced the text to wrap, and nothing bounded it, so a long
    translation overflowed the bar on a narrow screen. The text has to be capped at one line with
    an ellipsis, and the row has to be allowed to grow instead of clipping.
    """
    bar = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainBottomBar.kt').read_text('utf-8')
    assert re.search(r'\.height\(64\.dp\)', bar) is None, (
        'the fixed 64dp row height is back; the status text overflows the bar again')
    assert 'heightIn(min = 64.dp)' in bar, (
        'the bar no longer grows with its content, so a wrapped line is clipped')
    status = re.search(r'text = displayText,(.*?)\n\s*\)', bar, re.S)
    assert status, 'the status text is gone from the bottom bar'
    assert 'maxLines = 1' in status.group(1), (
        'the status text is unbounded again and can wrap out of the bar')
    assert 'TextOverflow.Ellipsis' in status.group(1), (
        'the status text is truncated without an ellipsis, so it ends mid-word')
    assert 'import androidx.compose.ui.text.style.TextOverflow' in bar, (
        'TextOverflow is used but not imported, so the bar will not compile')

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
    # The in-flight flag guards the button, so a cancelled pass that never clears it leaves the
    # button permanently dead. It has to be released on every exit path, including cancellation.
    assert re.search(r'try\s*\{(.*?)\}\s*finally\s*\{\s*flagRefreshRunning\.value = false', view_model, re.S), (
        'the in-flight flag is not cleared in a finally block; a cancelled pass leaves the '
        'refresh button permanently unresponsive')

def check_the_flag_path_is_covered_by_a_real_test():
    """Four releases shipped a green build with no flag on screen, so the tests have to be real.

    The regressions that reached the device were all invisible to the suite: tests written
    against a hand-made fake of the lookup instead of the parser, and a fixed row height that
    only overflows when the text is actually laid out. A test that only exercises a fake, or that
    never renders, cannot catch either.
    """
    tests = ROOT / 'V2rayNG/app/src/test/java/com/v2ray/ang'
    parser_tests = [
        tests / 'ui/main/RealPayloadProbe.kt',
        tests / 'ui/main/RealConfigUriProbe.kt',
        tests / 'handler/ServerFlaggedLookupTest.kt',
        tests / 'ui/main/MainCountryRowsTest.kt',
    ]
    for t in parser_tests:
        assert t.exists(), f'the flag regression test {t.name} is gone'
    payload = (tests / 'ui/main/RealPayloadProbe.kt').read_text('utf-8')
    assert 'ServerFlaggedLookup.parseVerdict' in payload, (
        'the provider payload is no longer parsed by the real parser in a test')
    assert 'countryCode' in payload, (
        'no test asserts the country reaches the row, so an empty flag stays invisible')
    # A fake proves the test's own expectations, not the app's behaviour. The real parser and the
    # real row update have to be the thing under test.
    for t in parser_tests:
        body = t.read_text('utf-8')
        assert 'FakeFlagService' not in body, (
            f'{t.name} tests a hand-made fake instead of the real lookup, so it cannot fail when '
            'the real one does')

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
