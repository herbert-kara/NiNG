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
    check_a_measured_delay_resolves_the_flags()
    check_the_bottom_bar_is_the_original_one()
    check_a_latency_test_also_resolves_the_flags()
    check_the_flag_walk_is_concurrent()
    check_the_row_has_one_flag_slot()
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

def check_a_measured_delay_resolves_the_flags():
    """The badge stayed on "unchecked" because nothing resolved it when the user looked.

    A measured delay is when the user is reading a row, so the lookup for those rows runs from
    the test result. Before this the only trigger was a background pass that walked the page one
    rate-limited address at a time, which is why the flag never appeared where it was expected.
    """
    vm = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt').read_text('utf-8')
    flush = re.search(r'private suspend fun flushPendingTestResults(.*?)\n    \}', vm, re.S)
    assert flush, 'the delay-result flush is gone'
    assert 'resolveLookupsFor(' in flush.group(1), (
        'a measured delay no longer triggers the lookup, so the row keeps "unchecked"')

def check_the_bottom_bar_is_the_original_one():
    """The bottom bar was rebuilt with a second button and a flag in it, and both were wrong.

    The bar is the status line and the connect control. A refresh button beside it read as dead,
    and a flag in it put the running connection's exit country where the user reads each profile's
    own location. It is back to the upstream shape: one button, no flag.
    """
    bar = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainBottomBar.kt').read_text('utf-8')
    assert 'SmallFloatingActionButton' not in bar, 'a second button is back in the bottom bar'
    assert 'CountryBadge' not in bar, 'a flag is back in the bottom bar'
    assert 'exitCountryCode' not in bar, 'the bottom bar takes an exit country again'
    assert 'MainAction.RefreshFlags' not in bar, 'the bottom bar dispatches a refresh again'
    screen = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainScreen.kt').read_text('utf-8')
    assert 'CountryBadge' not in screen, 'the screen still asks the bottom bar for a flag'
    assert 'isRefreshingFlags' not in screen, 'the screen still wires a flag-refresh state'
    assert 'MainAction.ToggleService' in bar, 'the bottom bar lost the connect control'
    assert 'MainAction.TestCurrentServer' in bar, 'the bottom bar lost tap-to-test'
    assert len(re.findall(r'FloatingActionButton\(', bar)) == 1, (
        'the bar must have exactly one button, the connect control')

def check_a_latency_test_also_resolves_the_flags():
    """The flags were refreshed on their own schedule, behind the measurement.

    The user measures the delays and then reads the page, so a latency test has to resolve the
    flags as part of the same action. A pass on a timer of its own stays behind, which is the
    state the flags were actually in.
    """
    vm = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt').read_text('utf-8')
    for action in ('MainAction.TestAllServers', 'MainAction.TestRealAllServers'):
        line = re.search(re.escape(action) + r' -> (.*)', vm)
        assert line, f'{action} has no handler'
        assert 'refreshFlags()' in line.group(1), (
            f'{action} measures delays but does not resolve the flags, so the page keeps a '
            'verdict from before the measurement')
    contract = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainContract.kt').read_text('utf-8')
    assert 'RefreshFlags' not in contract, 'the separate refresh action is back in the contract'
    assert 'RefreshFlags' not in vm, 'the separate refresh action is back'

def check_the_flag_walk_is_concurrent():
    """A one-row-at-a-time walk of a rate-limited page never reaches the last row.

    The walk was the bottleneck, not the network: the lookups are rate limited, so 128 rows came
    to hours. It has to overlap a bounded number of them and stay bounded.
    """
    batch = (APP / 'src/main/java/com/v2ray/ang/ui/main/FlagBatch.kt').read_text('utf-8')
    assert 'Semaphore' in batch, 'the walk is unbounded again'
    assert 'async' in batch, 'the lookups no longer overlap'
    assert 'FLAG_LOOKUP_CONCURRENCY' in batch, 'the concurrency bound is gone'
    assert re.search(r'targets\.map\s*\{', batch), (
        'the targets are walked in a plain loop again, so the walk is serial')
    test = ROOT / 'V2rayNG/app/src/test/java/com/v2ray/ang/ui/main/FlagBatchConcurrencyTest.kt'
    assert test.exists(), 'the regression test for a serial walk is gone'

def check_the_row_has_one_flag_slot():
    """The grey placeholder and the real flag were two badges for one fact.

    The row needs a single slot that fills in place: nothing while the lookup is outstanding, the
    real flag once it answers, and the verdict only when it is worth interrupting for.
    """
    badge = (APP / 'src/main/java/com/v2ray/ang/ui/main/CountryBadge.kt').read_text('utf-8')
    assert 'fun ServerFlagSlot(' in badge, 'the single flag slot is gone'
    slot = badge[badge.index('fun ServerFlagSlot('):]
    assert 'ProfileCountry.flagAsset(code)' in slot, 'the slot no longer fills from a country'
    pager = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainServerPager.kt').read_text('utf-8')
    assert 'ServerFlagSlot(row.serverCountryCode' in pager, 'the row no longer uses the flag slot'
    assert pager.count('CountryBadge(') == 0, 'the row renders a second, separate flag again'
    assert pager.count('FlaggedBadge(') == 0, 'the row renders the verdict outside the slot again'

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
    # A resolved verdict used to be dropped by the next list rebuild, so the badge fell back to
    # "unchecked" right after resolving. The carry-over and its test have to stay.
    carry = APP / 'src/main/java/com/v2ray/ang/ui/main/RowLookupCarryOver.kt'
    assert carry.exists(), 'the row carry-over is gone; a rebuild drops the verdict again'
    body = carry.read_text('utf-8')
    assert 'serverCountryCode = old.serverCountryCode' in body, 'the location flag is dropped on rebuild'
    assert 'flagStatus = old.flagStatus' in body, (
        'the reputation verdict is dropped on rebuild, so a resolved row falls back to "unchecked"')
    view_model = (APP / 'src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt').read_text('utf-8')
    assert 'RowLookupCarryOver.carry(' in view_model, (
        'the list rebuild no longer uses the carry-over, so lookups are lost on every update')
    carry_test = ROOT / 'V2rayNG/app/src/test/java/com/v2ray/ang/ui/main/RowLookupCarryOverTest.kt'
    assert carry_test.exists(), 'the regression test for a verdict lost on rebuild is gone'
    assert 'aResolvedVerdictSurvivesAListRebuild' in carry_test.read_text('utf-8'), (
        'no test asserts a resolved verdict survives a rebuild')

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
