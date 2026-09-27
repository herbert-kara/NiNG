"""Static NiNG identity checks; Android compilation remains in CI."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'V2rayNG/app'
TEST = ROOT / 'V2rayNG/app/src/test/java'

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
    check_a_lock_does_not_serialise_the_lookups()
    check_a_measured_delay_forces_a_fresh_lookup()
    check_the_palette_is_the_whole_theme()
    check_the_connect_button_is_blue_and_ping_is_untouched()
    check_theme_pairs_stay_readable()
    check_a_rewritten_function_keeps_its_helpers_and_returns()
    check_the_launcher_wordmark_is_teal_and_20_percent_smaller()
    check_the_documented_colour_exceptions_hold()
    check_a_flag_walk_survives_its_own_publish()
    check_the_flag_lookup_asks_through_the_tunnel()
    check_flag_diagnostics_use_a_level_that_survives_the_default()
    check_the_flag_path_says_where_it_stops()
    check_the_flag_path_records_without_depending_on_the_settings_store()
    check_the_country_lookup_client_is_a_valid_read_write_property()
    check_the_tunnel_route_is_injectable_and_testable()
    check_the_route_test_stubs_speak_the_seams_language()
    check_the_country_walk_has_a_reachable_provider()
    check_concurrent_lookups_of_one_address_share_a_round_trip()
    check_no_concurrency_test_freezes_the_clock_it_depends_on()
    check_a_concurrency_test_can_reach_concurrency()
    check_a_concurrency_test_uses_only_fake_dependencies()
    check_a_concurrency_test_uses_addresses_that_reach_the_provider()
    check_the_sources_have_no_defect_a_compiler_would_catch()
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

def check_a_lock_does_not_serialise_the_lookups():
    """One mutex around the whole lookup made a concurrent walk queue up behind the network.

    A lock held across the request is what defeated the concurrent walk: every lookup waited for
    the previous one's full round trip, so eight at a time still went out one at a time and a page
    still took minutes. Only the pacing between request starts needs a lock.
    """
    for name in ("ServerFlaggedLookup", "ServerCountryLookup"):
        src = (APP / ("src/main/java/com/v2ray/ang/handler/" + name + ".kt")).read_text("utf-8")
        body = src[src.index("suspend fun resolve("):]
        body = body[:body.index("private suspend fun defaultFetch")]
        assert "rateLimit()" in body, name + " no longer paces its requests"
        # Any use of a lock that spans the request itself is the defect: it makes the next
        # lookup wait for this one's full round trip. Reject a lock wrapping the whole function
        # body, and reject a pacing lock that is held around the request rather than released
        # before it.
        assert not re.search(r"suspend fun resolve[(][^)]*[)][^\n{]*[=]\s*[a-zA-Z]+[.]withLock", body), (
            name + " wraps the whole lookup in a lock, so a concurrent walk is serialised again "
            "and a page of flags never fills in")
        pacing = re.search(r"private suspend fun rateLimit[(][)] = ([a-zA-Z]+)[.]withLock", body + src)
        assert pacing, name + " does not pace its request starts under its own lock"
        start_line = src[:body.index("rateLimit()")].count("\n")
        # rateLimit() must be called, then the request issued after the lock is released: the
        # guard above rejects the wrapper form, and this rejects an inline withLock around it.
        assert not re.search(r"withLock \{\s*\n(?:[^\n]*\n){0,6}?\s*withTimeoutOrNull", body), (
            name + " issues the request inside a lock, so lookups cannot overlap")
        assert "private val pacing = Mutex()" in src, name + " has no separate request pacing"
    test = ROOT / "V2rayNG/app/src/test/java/com/v2ray/ang/handler/LookupConcurrencyTest.kt"
    assert test.exists(), "the regression test for a serialising lock is gone"


def check_a_measured_delay_forces_a_fresh_lookup():
    """The measured-delay path kept the old serial loop and replayed the cache.

    Two separate defects hid behind one symptom: the per-delay path was still a plain loop, and
    it called resolve without forcing, so it replayed a verdict cached before the measurement.
    Both left the row showing whatever it already had.
    """
    vm = (APP / "src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt").read_text("utf-8")
    # Slice to the next top-level declaration: a lazy regex stops at the first closing brace,
    # which lands inside a lambda and silently reads a truncated body as a missing walk.
    start = vm.index("private fun resolveLookupsFor(")
    rest = vm[start:]
    nxt = re.search(r"\n    (?:private |internal |override )?(?:suspend )?fun ", rest[10:])
    src = rest[:nxt.start() + 10] if nxt else rest
    assert src, "the per-delay lookup is gone"
    assert "runFlagBatch(" in src, (
        "the per-delay path is not the concurrent walk, so measured rows resolve one at a time")
    assert src.count("force = true") >= 2, (
        "the per-delay path does not force a fresh query, so it replays a pre-test verdict")
    assert re.search(r"for [(]", src) is None, (
        "the per-delay path still walks its targets in a plain loop")
    # The flag has to be threaded all the way into the lookup call, not just present in the
    # batch arguments: a lambda that ignores it and calls resolve(address) replays the cache
    # while the surrounding force = true still makes the guard look satisfied. Every call in
    # this function is checked, because a second, healthy-looking one elsewhere in the file
    # would otherwise satisfy a substring search for the wrong one.
    calls = re.findall(r"serverFlags[.]resolve[(]([^)]*)[)]", src)
    assert calls, "the per-delay path never calls the reputation lookup"
    for arg in calls:
        assert arg == "address, force", (
            "the per-delay path calls serverFlags.resolve(" + arg + ") instead of threading the "
            "force flag, so a measured row keeps the verdict cached before the measurement")


PALETTE = ("272727", "FED766", "009FB7", "696773", "EFF1F3")
# Status colours carry meaning, so they are not decoration and stay out of the palette: the ping
# colours were explicitly kept by the user, and red means an error.
# Outside the palette on purpose, each with a reason recorded in
# check_the_documented_colour_exceptions_hold():
#   009966  the latency/verdict green, semantic
#   FF0099   the second latency colour, semantic
#   D32F2F  error red, no palette member means "stop"
#   9E2323 350C0C F3D6D4 D50000  the error red's tones for containers
#   E0E0E0 424242  the row separators, restored to their pre-sweep greys
ALLOWED_OUTSIDE = {
    "009966", "FF0099", "D32F2F", "9E2323", "F3D6D4", "350C0C", "D50000",
    "E0E0E0", "424242",
}
# Pure white is the top of the mist anchor, not an extra colour.
SCALED_ACCEPTED = {"FFFFFF", "000000"}


def _lum(h):
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    c = [(x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4) for x in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def _contrast(a, b):
    la, lb = _lum(a), _lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def _is_scaled_member(rgb, anchor, lo=0.24, hi=4.2):
    """True when rgb is a darkened or lightened member of the anchor rather than another hue.

    Comparing channel order alone is not enough: every near-grey has a "consistent" channel order
    against every other near-grey, so a violet sneaks past a charcoal test. Hue and saturation
    have to move together with lightness for this to be the same colour.
    """
    import colorsys
    def hsv(h):
        r, g, b = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
        return colorsys.rgb_to_hsv(r, g, b)
    hh, sh, vh = hsv(rgb)
    ha, sa, va = hsv(anchor)
    if va == 0:
        return vh <= 0.08
    # A near-grey anchor can only produce a near-grey member.
    if sa < 0.12 and sh > 0.18:
        return False
    dh = abs(hh - ha)
    dh = min(dh, 1 - dh)
    scale = vh / va
    return (dh <= 0.06 and lo <= scale <= hi) or (sh <= 0.12 and vh <= 0.08)


def check_the_palette_is_the_whole_theme():
    """Every themed colour was remapped to the five-colour palette, ping excepted.

    The connect control was asked to be blue, the latency colours were asked to stay, and
    everything else was asked to become the nearest member of the palette. A stray literal is how
    the old orange creeps back in one component at a time.
    """
    theme = (APP / "src/main/java/com/v2ray/ang/ui/compose/Theme.kt").read_text("utf-8")
    for lit in re.findall(r"Color[(]0x[0-9A-Fa-f]{8}[)]", theme):
        rgb = re.sub(r"[^0-9A-Fa-f]", "", lit)[-6:].upper()
        if rgb in PALETTE or rgb in ALLOWED_OUTSIDE or rgb in SCALED_ACCEPTED:
            continue
        assert any(_is_scaled_member(rgb, a) for a in PALETTE), (
            "the theme has a colour outside the palette again: 0x" + rgb)
    assert "Color(0xFF009FB7)" in theme, "the teal anchor is gone from the theme"


def check_the_connect_button_is_blue_and_ping_is_untouched():
    """The two colours the user pinned by name: the button goes blue, the latency colours stay.

    They are semantic, not decorative, so a palette sweep that moves them silently changes what a
    row means rather than how it looks.
    """
    theme = (APP / "src/main/java/com/v2ray/ang/ui/compose/Theme.kt").read_text("utf-8")
    def val_of(name):
        m = re.search(r"val " + name + r" = Color[(]0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})[)]", theme)
        return m.group(1).upper() if m else None
    assert val_of("colorFabActive") == "009FB7", (
        "the connect button is not blue again: 0x" + str(val_of("colorFabActive")))
    assert val_of("colorPing") == "009966", (
        "the latency green was remapped and lost its meaning: 0x" + str(val_of("colorPing")))
    assert val_of("colorPingRed") == "FF0099", (
        "the latency red was remapped and lost its meaning: 0x" + str(val_of("colorPingRed")))
    # The flag tints are pinned in check_the_documented_colour_exceptions_hold(). Only two of the
    # four are palette members: safe is the latency green by the user's instruction, and only
    # caution and unknown are anchors.
    risk = (APP / "src/main/java/com/v2ray/ang/ui/main/RiskBadge.kt").read_text("utf-8")
    assert "Color(0xFFFED766)" in risk, "the caution flag is not on the palette"
    assert "Color(0xFF696773)" in risk, "the unknown flag is not on the palette"


_KEYWORDS = {
    "if", "for", "while", "when", "return", "suspend", "inline", "constructor", "super", "this",
    "object", "class", "fun", "val", "var", "else", "try", "catch", "do", "throw", "is", "in",
    "as", "!in", "!is", "step", "until", "downTo", "reversed",
}


# Standard-library and Compose idioms that are extension calls or scope functions, not local helpers.
_IDIOMS = {
    "run", "let", "also", "apply", "with", "takeIf", "takeUnless", "getOrNull", "getOrElse",
    "getOrDefault", "use", "forEach", "map", "filter", "first", "firstOrNull", "toList", "sorted",
    "sortedBy", "isNullOrEmpty", "isNotEmpty", "orEmpty", "joinToString", "plus", "minus", "format",
    "startsWith", "endsWith", "removePrefix", "removeSuffix", "toInt", "toLong", "toFloat",
    "substringBefore", "substringAfter", "trim", "lowercase", "uppercase", "contains", "isBlank",
    # JDK constructors and statics that are legitimately unqualified.
    "Thread", "Executors", "TimeUnit", "InetAddress", "InetSocketAddress", "System", "String",
    "Integer", "Long", "Float", "Double", "Boolean", "Byte", "Short", "Char", "IllegalStateException",
    "IllegalArgumentException", "RuntimeException", "Exception", "ArrayList", "HashMap", "HashSet",
    "LinkedHashMap", "LinkedHashSet", "Collections", "Objects", "Optional", "Timer", "UUID",
    # Kotlin stdlib factory functions.
    "listOf", "mutableListOf", "setOf", "mutableSetOf", "mapOf", "mutableMapOf", "arrayOf",
    "emptyList", "emptyMap", "emptySet", "sequenceOf", "buildString", "buildList", "buildMap",
    "require", "requireNotNull", "check", "checkNotNull", "error", "TODO", "lazy", "arrayListOf",
    "Pair", "Triple", "lazyOf", "Result", "runCatching", "let", "also", "apply", "with",
    "Regex", "MatchResult", "StringBuilder", "CharArray", "IntArray", "LongArray", "ByteArray",
    "BooleanArray", "FloatArray", "DoubleArray", "List", "MutableList", "Set", "MutableSet",
    "Map", "MutableMap", "Iterator", "Sequence", "Comparable", "Number", "Unit", "Nothing",
    "Deferred", "CompletableDeferred", "CoroutineScope", "CoroutineDispatcher", "Job", "Mutex",
    "MutexWithLock", "Semaphore", "withTimeoutOrNull", "withContext", "Dispatchers", "launch",
    "async", "awaitAll", "await", "delay", "runInterruptible", "coroutineScope", "supervisorScope",
    "CancellationException", "Logger", "LoggerFactory", "HttpURLConnection", "URL", "URI",
}


def check_the_sources_have_no_defect_a_compiler_would_catch():
    """Three builds died on mistakes a compiler finds in seconds and a text pass can also find.

    Duplicate imports, a helper whose declared parameter type does not match its body's, and a
    function that returns the wrong type are all local, all mechanical, and all cost a full native
    build to discover. Nothing here needs to understand Kotlin; it only needs to check that the
    file is internally consistent.
    """
    files = list((APP / "src/main/java").rglob("*.kt")) + list((APP / "src/test/java").rglob("*.kt"))
    for src in files:
        text = src.read_text("utf-8")
        rel = src.relative_to(APP)
        imports = [l.strip() for l in text.splitlines() if l.startswith("import ")]
        dupes = sorted({i for i in imports if imports.count(i) > 1})
        assert not dupes, str(rel) + " imports " + dupes[0] + " twice, which the compiler rejects"
        # Brackets counted with a scanner rather than a regex: a hand edit that drops a closing
        # brace must not read as balanced. A regex cannot do this reliably, because a Kotlin string
        # may hold any character, including the ones a pattern treats as syntax.
        depth = {"{": 0, "(": 0, "[": 0}
        pairs = {"}": "{", ")": "(", "]": "["}
        i, n = 0, len(text)
        while i < n:
            c = text[i]
            two = text[i:i + 2]
            if two == "//":
                i = text.find("\n", i)
                if i < 0: break
                continue
            if two == "/*":
                j = text.find("*/", i + 2)
                i = n if j < 0 else j + 2
                continue
            if text[i:i + 3] == '"""':
                j = text.find('"""', i + 3)
                i = n if j < 0 else j + 3
                continue
            if c == '"':
                i += 1
                while i < n and text[i] != '"':
                    i += 2 if text[i] == "\\" else 1
                i += 1
                continue
            if c == "'":
                i += 1
                while i < n and text[i] != "'":
                    i += 2 if text[i] == "\\" else 1
                i += 1
                continue
            if c in depth:
                depth[c] += 1
            elif c in pairs:
                depth[pairs[c]] -= 1
            i += 1
        for opener in depth:
            closer = {"{": "}", "(": ")", "[": "]"}[opener]
            assert depth[opener] == 0, (
                str(rel) + " leaves " + str(depth[opener]) + " " + opener
                + " unclosed, so it cannot compile")
        # A signature that promises one type and returns another. A suspend (String) -> T lambda
        # parameter is matched on its own, since the inner parentheses are not the call's own.
        for m in re.finditer(r"\bfun\s+(fetchOnce|fetchFrom|fetch)\b", text):
            name = m.group(1)
            sig = text[m.start():m.start() + 300]
            lam = re.search(r"request\s*:\s*suspend\s*\(\s*String\s*\)\s*->\s*([\w?<>]+)", sig)
            if not lam:
                continue
            ret = re.search(r"\)\s*:\s*([\w?<>]+)", sig)
            assert ret, str(rel) + ": " + name + "() has no declared return type to compare"
            assert lam.group(1) == ret.group(1), (
                str(rel) + ": " + name + "() takes a request returning " + lam.group(1)
                + " but declares " + ret.group(1) + ", so every call is a type error")
        # A block-bodied function whose last statement is a bare value is a discarded result.
        for m in re.finditer(r"\n    (?:private |internal )?(?:suspend )?fun (\w+)\([^)]*\)(?:: ([\w?<>]+))? \{", text):
            name = m.group(1)
            if name in ("main", "toString", "equals", "hashCode", "invoke", "get", "compareTo"):
                continue
            i = m.end()
            depth, j = 1, i
            while j < len(text) and depth:
                if text[j] == "{": depth += 1
                elif text[j] == "}": depth -= 1
                j += 1
            body = text[i:j - 1]
            if "return" in body:
                continue
            tail = [l.strip() for l in body.rstrip().splitlines() if l.strip()]
            if not tail or tail[-1].endswith(("}", ")", ",")):
                continue
            # A trailing bare expression in a Unit function is fine; in a valued one it is not.
            assert not re.fullmatch(r"[\w.]+\([^()]*\)", tail[-1]), (
                str(rel) + ": " + name + "() ends with the discarded expression " + tail[-1]
                + " and returns Unit, so the result never leaves the function")


def check_the_sources_have_no_defect_a_compiler_would_catch():
    """Three builds died on mistakes a compiler finds in seconds and a text pass can also find.

    Duplicate imports, a helper whose declared parameter type does not match its body's, and a
    function that returns the wrong type are all local, all mechanical, and all cost a full native
    build to discover. Nothing here needs to understand Kotlin; it only needs to check that the
    file is internally consistent.
    """
    files = list((APP / "src/main/java").rglob("*.kt")) + list((APP / "src/test/java").rglob("*.kt"))
    for src in files:
        text = src.read_text("utf-8")
        rel = src.relative_to(APP)
        imports = [l.strip() for l in text.splitlines() if l.startswith("import ")]
        dupes = sorted({i for i in imports if imports.count(i) > 1})
        assert not dupes, str(rel) + " imports " + dupes[0] + " twice, which the compiler rejects"
        # Brackets counted with a scanner rather than a regex: a hand edit that drops a closing
        # brace must not read as balanced. A regex cannot do this reliably, because a Kotlin string
        # may hold any character, including the ones a pattern treats as syntax.
        depth = {"{": 0, "(": 0, "[": 0}
        pairs = {"}": "{", ")": "(", "]": "["}
        i, n = 0, len(text)
        while i < n:
            c = text[i]
            two = text[i:i + 2]
            if two == "//":
                i = text.find("\n", i)
                if i < 0: break
                continue
            if two == "/*":
                j = text.find("*/", i + 2)
                i = n if j < 0 else j + 2
                continue
            if text[i:i + 3] == '"""':
                j = text.find('"""', i + 3)
                i = n if j < 0 else j + 3
                continue
            if c == '"':
                i += 1
                while i < n and text[i] != '"':
                    i += 2 if text[i] == "\\" else 1
                i += 1
                continue
            if c == "'":
                i += 1
                while i < n and text[i] != "'":
                    i += 2 if text[i] == "\\" else 1
                i += 1
                continue
            if c in depth:
                depth[c] += 1
            elif c in pairs:
                depth[pairs[c]] -= 1
            i += 1
        for opener in depth:
            closer = {"{": "}", "(": ")", "[": "]"}[opener]
            assert depth[opener] == 0, (
                str(rel) + " leaves " + str(depth[opener]) + " " + opener
                + " unclosed, so it cannot compile")
        # A signature that promises one type and returns another. A suspend (String) -> T lambda
        # parameter is matched on its own, since the inner parentheses are not the call's own.
        for m in re.finditer(r"\bfun\s+(fetchOnce|fetchFrom|fetch)\b", text):
            name = m.group(1)
            sig = text[m.start():m.start() + 300]
            lam = re.search(r"request\s*:\s*suspend\s*\(\s*String\s*\)\s*->\s*([\w?<>]+)", sig)
            if not lam:
                continue
            ret = re.search(r"\)\s*:\s*([\w?<>]+)", sig)
            assert ret, str(rel) + ": " + name + "() has no declared return type to compare"
            assert lam.group(1) == ret.group(1), (
                str(rel) + ": " + name + "() takes a request returning " + lam.group(1)
                + " but declares " + ret.group(1) + ", so every call is a type error")
        # A block-bodied function whose last statement is a bare value is a discarded result.
        for m in re.finditer(r"\n    (?:private |internal )?(?:suspend )?fun (\w+)\([^)]*\)(?:: ([\w?<>]+))? \{", text):
            name = m.group(1)
            if name in ("main", "toString", "equals", "hashCode", "invoke", "get", "compareTo"):
                continue
            i = m.end()
            depth, j = 1, i
            while j < len(text) and depth:
                if text[j] == "{": depth += 1
                elif text[j] == "}": depth -= 1
                j += 1
            body = text[i:j - 1]
            if "return" in body:
                continue
            tail = [l.strip() for l in body.rstrip().splitlines() if l.strip()]
            if not tail or tail[-1].endswith(("}", ")", ",")):
                continue
            # A trailing bare expression in a Unit function is fine; in a valued one it is not.
            assert not re.fullmatch(r"[\w.]+\([^()]*\)", tail[-1]), (
                str(rel) + ": " + name + "() ends with the discarded expression " + tail[-1]
                + " and returns Unit, so the result never leaves the function")


def check_a_concurrency_test_uses_addresses_that_reach_the_provider():
    """A test can pass through every line of the code under test without calling it.

    This one asked for 10.0.0.x, which is RFC1918. isPublicIp() rejects it, so resolvePublicIp()
    returned null, fetchOnce() was never entered, and the batch finished in zero virtual
    milliseconds having measured a short circuit rather than any locking. The assertion then read
    that as proof of serialisation, which is what sent four builds looking in the wrong place.

    The guard resolves the same private ranges the production code rejects, and requires a test that
    asserts on provider behaviour to use addresses that survive the check.
    """
    def is_public(ip: str) -> bool:
        try:
            b = [int(x) for x in ip.split(".")]
        except ValueError:
            return True          # not a literal; nothing to judge
        if len(b) != 4 or any(x > 255 for x in b):
            return True
        if b[0] in (0, 10, 127) or b[0] >= 224:
            return False
        if b[0] == 172 and 16 <= b[1] <= 31:
            return False
        if b[0] == 192 and b[1] == 168:
            return False
        if b[0] == 100 and 64 <= b[1] <= 127:
            return False
        if b[0] == 169 and b[1] == 254:
            return False
        if b[0] == 198 and b[1] in (18, 19):
            return False
        return True

    for rel in ("src/test/java/com/v2ray/ang/handler/LookupConcurrencyTest.kt",):
        src = (APP / rel).read_text("utf-8")
        code = re.sub(r"/\*[\s\S]*?\*/", "", src)
        code = re.sub(r"//[^\n]*", "", code)
        # Resolve a template like "1.1.1.$i" by substituting a value in the host part.
        for tmpl in set(re.findall(r'"(\d+\.\d+\.\d+\.\$\w+)"', code)):
            concrete = tmpl[: tmpl.rindex(".")] + ".7"
            assert is_public(concrete), (
                "a concurrency test uses " + tmpl + " (-> " + concrete + "), which isPublicIp() "
                "rejects, so resolve() short-circuits before the locking under test is ever reached "
                "and the batch finishes in zero time. Use a public literal.")


def check_a_concurrency_test_uses_only_fake_dependencies():
    """A coroutine test that reaches a real thread pool measures the thread pool, not the code.

    Two of these tests failed for that reason. One built a ServerCountryLookup without resolveDns,
    so it fell through to the platform DNS on a real ThreadPoolExecutor; runTest's virtual clock
    does not govern that thread, so the round trips stopped overlapping and the assertion read as
    proof of serialisation. The other asserted a country its own stub never returns, so it had been
    failing since it was written and was only ever run once the earlier ones passed.

    Both are checkable: a lookup under test names every dependency it has, and a stub's return
    value appears in the assertions that follow it.
    """
    for rel in ("src/test/java/com/v2ray/ang/handler/LookupConcurrencyTest.kt",):
        src = (APP / rel).read_text("utf-8")
        code = re.sub(r"/\*[\s\S]*?\*/", "", src)
        code = re.sub(r"//[^\n]*", "", code)
        for m in re.finditer(r"ServerCountryLookup\(([^)]*)\)", code, re.S):
            args = m.group(1)
            assert "resolveDns" in args, (
                "a ServerCountryLookup in a coroutine test is built without resolveDns, so it "
                "reaches the platform DNS on a real thread pool that the virtual clock does not "
                "govern. Pass resolveDns and the test measures the code instead of the pool.")
        # A stub that answers one thing cannot be asserted to answer another. Read the country
        # the stub returns and the countries the rest of the test asserts, and compare the sets.
        for stub in re.finditer(r'isocode"\s*:\s*"([A-Z]{2})"', code):
            country = stub.group(1)
            fn_start = code.rfind("\n    fun ", 0, stub.start())
            fn_end = code.find("\n    fun ", stub.end())
            body = code[stub.end(): fn_end if fn_end > 0 else len(code)]
            asserted = set(re.findall(r'assert(?:Equals|True)\(\s*"([A-Z]{2})"', body))
            wrong = {a for a in asserted if a != country}
            assert not wrong, (
                "the stub answers " + country + " but the test asserts " + str(sorted(wrong))
                + ", so that assertion cannot hold for any implementation")



def check_a_concurrency_test_can_reach_concurrency():
    """A test can be unfalsifiable by arithmetic: a 100ms fetch and a 250ms pacing gap mean the
    first request is always over before the second one may start, so "these overlap" is false
    whatever the code does. That test sat failing for two days while it was the thing at fault.

    The guard reads the pacing gap out of the production code and the fetch delay out of the test,
    and requires the fetch to be able to outlast the gap. It also requires the two providers to
    share one gap, since a literal in one file and a constant in the other is how they drift.
    """
    gaps = {}
    for name in ("ServerFlaggedLookup", "ServerCountryLookup"):
        src = (APP / ("src/main/java/com/v2ray/ang/handler/" + name + ".kt")).read_text("utf-8")
        m = re.search(r"REQUEST_GAP_MS = (\d+)L", src)
        assert m, name + " has no named REQUEST_GAP_MS, so its pacing is a literal nobody can find"
        assert "delay(1100" not in src, name + " still paces on a literal 1100ms"
        gaps[name] = int(m.group(1))
        assert len(set(gaps.values())) == 1, (
            "the two providers pace differently: " + str(gaps) + ", so one page of flags is "
            "throttled by whichever is slower")
    gap = gaps["ServerFlaggedLookup"]
    test = (APP / "src/test/java/com/v2ray/ang/handler/LookupConcurrencyTest.kt").read_text("utf-8")
    fn = re.search(r"fun concurrentResolvesOverlapTheirRoundTrips[(].*?\n    \}", test, re.S)
    assert fn, "the overlap test is gone, so nothing checks that a flag page still fills in"
    for d in re.findall(r"delay\((\d+)\)", fn.group(0)):
        assert int(d) > gap, (
            "the overlap test fetches for " + d + "ms against a " + str(gap) + "ms pacing gap, so "
            "the first request always finishes before the second may start and the assertion can "
            "never hold for any implementation")
    fn2 = re.search(r"fun theCountryLookupAlsoOverlapsItsRoundTrips[(].*?\n    \}", test, re.S)
    assert fn2, "the country overlap test is gone"
    for d in re.findall(r"delay\((\d+)\)", fn2.group(0)):
        assert int(d) > gap, (
            "the country overlap test fetches for " + d + "ms against a " + str(gap) + "ms gap")


def check_no_concurrency_test_freezes_the_clock_it_depends_on():
    """Three tests failed for two days because their own clock stub was the bug.

    The lookups take a `nowMillis` so they can be tested without sleeping. Three of them passed a
    constant zero, and that zero is not neutral: rateLimit() reads it to decide how long to wait, so
    elapsed was always 0 and every lookup paid the full 1100ms gap, which looks exactly like the
    serialisation the tests were written to catch. The same constant made a cache entry's expiry
    (now + TTL) unreachable, so a forced refresh read a stale verdict and the test saw FR where it
    expected DE. The production code was right in both cases and the tests were measuring the stub.

    A frozen clock is only safe for a test that never reads it back through elapsed-time logic, so
    the guard rejects the constant wherever the file also asserts on ordering, overlap or expiry.
    """
    test_root = APP / "src/test/java"
    for src in test_root.rglob("*.kt"):
        text = src.read_text("utf-8")
        if "nowMillis" not in text:
            continue
        code = re.sub(r"/\*[\s\S]*?\*/", "", text)
        code = re.sub(r"//[^\n]*", "", code)
        frozen = re.findall(r"nowMillis\s*=\s*\{\s*0L\s*\}", code)
        if not frozen:
            continue
        # A constant clock is fine when a test drives the clock by hand; it is the *uncontrollable*
        # one that breaks, because the code under test still reads it.
        manual = re.search(r"var clock = 0L", code)
        assert manual, (
            str(src.relative_to(test_root)) + " passes a frozen nowMillis to a lookup; rateLimit() "
            "and the cache TTL both read it, so pacing and expiry stop meaning anything. Use "
            "{ testScheduler.currentTime } so the virtual clock advances with the test.")
        if "testScheduler.currentTime" in code:
            continue


def check_concurrent_lookups_of_one_address_share_a_round_trip():
    """Splitting the single lock dropped deduplication as a side effect.

    The old resolve() held one mutex, so two callers wanting the same address necessarily shared
    one request. Splitting it into a cache lock and a pacing lock let those two requests race, and
    four of the tests caught it. Deduplication is a separate property from pacing and needs its own
    guard, or the next lock change takes it away again.

    The first implementation held a Deferred per address, which is the textbook answer and does not
    work here: the Deferred has to be started in a scope that is not the caller's, and a detached
    coroutine is cancelled out from under runTest, so the tests failed for a reason no amount of
    source inspection would show. What is in place is a lock per address, so the guard looks for
    that and refuses the Deferred coming back.
    """
    for name in ("ServerFlaggedLookup", "ServerCountryLookup"):
        src = (APP / ("src/main/java/com/v2ray/ang/handler/" + name + ".kt")).read_text("utf-8")
        # Strip comments first: a KDoc that explains the mechanism would otherwise keep the guard
        # green after the mechanism is gone.
        code = re.sub(r"/\*[\s\S]*?\*/", "", src)
        code = re.sub(r"//[^\n]*", "", code)
        assert re.search(r"private val perAddress\s*=", code), (
            name + " has no per-address lock, so equal addresses race each other")
        body = re.search(r"    suspend fun resolve[(].*?\n    \}", src, re.S)
        assert body, name + " has no resolve() to follow"
        assert "fetchOnce(" in body.group(0), (
            name + ": resolve() no longer goes through the shared round trip, so equal addresses "
            "race each other again")
        fn = re.search(r"private suspend fun fetchOnce[(].*?\n    \}", src, re.S)
        assert fn, name + " has no fetchOnce()"
        f = fn.group(0)
        assert "computeIfAbsent" in f or "perAddress[" in f, (
            name + ": fetchOnce() does not look its lock up by address, so it serialises everything")
        assert "withLock" in f, name + ": fetchOnce() does not hold a lock across the request"
        # The lock only deduplicates if the queued caller re-reads the cache once it gets in. A
        # cache check that happens before the lock is a check both callers pass, and both fetch.
        inner = f[f.index("withLock {"):] if "withLock {" in f else ""
        assert re.search(r"cache\[key\].*takeIf", inner, re.S), (
            name + ": fetchOnce() does not re-read the cache once it holds the lock, so a caller "
            "that queued behind the first one repeats the request instead of reading its result")
        fcode = re.sub(r"/\*[\s\S]*?\*/", "", f)
        fcode = re.sub(r"//[^\n]*", "", fcode)
        assert "finally" in fcode, (
            name + ": a failed fetchOnce() would leave its lock in the map forever")
        assert re.search(r"perAddress\.remove\(", fcode), (
            name + ": fetchOnce() never drops its lock, so the map grows with every address seen")
        # A detached coroutine is the mistake this replaced; it must not creep back.
        assert "async(" not in code, (
            name + ": fetchOnce() starts a detached coroutine again, which runTest cancels")
        assert "Deferred" not in code, (
            name + ": a shared Deferred is back; it needs a detached scope to start")

def check_the_country_walk_has_a_reachable_provider():
    """proxycheck.io answers in 0.3s from some networks and not at all from others.

    Measured while fixing the missing flag: the verdict provider blackholes every request from one
    egress (21s, zero bytes, connect never completes) while ipwho.is answers 5.180.82.45 in 0.3s.
    The verdict walk and the country walk are separate jobs, so a stalled provider costs the
    verdict, not the flag, but only as long as the country walk has a provider that answers and
    only as long as the walks are genuinely independent. Both are structural, so both are checked.
    """
    country = (APP / "src/main/java/com/v2ray/ang/handler/ServerCountryLookup.kt").read_text("utf-8")
    eps = re.search(r"COUNTRY_ENDPOINTS = listOf\(([^)]*)\)", country, re.S)
    assert eps, "the country lookup has no provider list, so a flag depends on one host"
    # Both schemes: a plain-HTTP provider is one of the five, and a regex that only reads https
    # hides it from the count, the pin and the cleartext check at once.
    hosts = re.findall(r'"https?://([^/"]+)/', eps.group(1))
    assert len(hosts) >= 3, (
        "the country lookup has " + str(len(hosts)) + " provider(s) " + str(hosts) + "); providers "
        "blackhole whole networks one at a time, so a short list makes the flag depend on which "
        "hosts the device can reach")
    # A count cannot catch a removal from a list of five, so the two measured to answer fastest
    # are pinned by name: ipwho.is in 0.3s and ip-api.com in 0.6s, both returning DE for the
    # reported rows while proxycheck.io blackholed the same network for 21s.
    # Ordering: TLS first, plain HTTP only as the last resort. ip-api.com answers in clear and is
    # reachable from networks that refuse the TLS providers, so it is configured -- but a forged
    # country is a wrong flag, so it must never be reached while a TLS provider is still untried.
    eps_block = country[country.index("COUNTRY_ENDPOINTS = listOf("):]
    eps_block = eps_block[:eps_block.index("\n        )")]
    order = re.findall(r'"(https?)://', eps_block)
    if "http" in order:
        first_plain = order.index("http")
        assert order[:first_plain] == ["https"] * first_plain, (
            "a plain-HTTP provider is configured before a TLS one (" + str(order) + "), so the "
            "unencrypted request is made while a TLS provider is still untried")
        assert order[-1] == "http", (
            "the plain-HTTP provider is not last (" + str(order) + "); it answers faster and reaches "
            "networks that refuse TLS, so it is wanted -- but only after every TLS one has failed")
    for required in ("ipwho.is", "ip-api.com"):
        assert required in hosts, (
            required + " is gone from the country providers " + str(hosts) + "; it was the one "
            "answering fastest from the network where the verdict provider does not answer at all")
    # The verdict provider is allowed to be flaky, so the country walk must not be able to stall
    # on it: its connect timeout has to be short enough that a dead provider still leaves a page.
    m = re.search(r"connectTimeout\((\d+),\s*TimeUnit\.SECONDS?\)", country)
    assert m, "the country lookup sets no connect timeout, so a blackholed host stalls the page"
    assert int(m.group(1)) <= 5, (
        "the country lookup waits " + m.group(1) + "s to connect; a blackholed host then costs "
        "every row that much before the next provider is tried")
    # A provider whose field name the reader does not accept returns null and costs a round trip
    # while looking like a provider that had nothing to say.
    reader = country[country.index("internal fun parseResponse"):]
    reader = reader[:reader.index("\n        }")]
    for key in ("country_code", "countryCode", "country"):
        assert '"%s"' % key in reader, (
            "the tolerant reader stopped accepting " + key + ", so a provider that spells the field "
            "that way now returns null")
    # Plain-HTTP providers are only usable while cleartext is permitted, which the app sets.
    manifest = (APP / "src/main/AndroidManifest.xml").read_text("utf-8")
    for scheme, rest in re.findall(r'"(https?)://([^/"]+)/', eps.group(1)):
        if scheme == "http":
            assert 'usesCleartextTraffic="true"' in manifest, (
                "a provider is plain HTTP (" + rest + ") but the manifest does not permit "
                "cleartext, so it is rejected before the request is sent")
    flagged = (APP / "src/main/java/com/v2ray/ang/handler/ServerFlaggedLookup.kt").read_text("utf-8")
    assert "proxycheck.io" in flagged, "the verdict provider is gone, so the guard is stale"


def check_the_route_test_stubs_speak_the_seams_language():
    """Four route tests failed on expected DE but was null, and the tests were the thing at fault.

    The fetch seam is (String) -> String? and its result goes straight into
    ProfileCountry.normalize, which wants a bare country code. These four returned a provider body
    -- {"country_code":"DE"} -- so normalize saw a 19-character string, found no country in it, and
    returned null. The five tests written alongside them already returned "DE" and "US", because
    whoever wrote them read the seam instead of the provider.

    The symptom points the other way: a null from the lookup looks exactly like a blocked provider,
    a dead route, or a request that was never made, which is where the previous two days went. A
    test that returns the wrong type fails the same way as the bug it is looking for.
    """
    tests = TEST / "com/v2ray/ang/handler/CountryTunnelRouteTest.kt"
    body = tests.read_text("utf-8")
    stubs = re.findall(r'fetch\s*=\s*\{[^}]*?"([^"]*)"', body) + \
        re.findall(r'fetch: suspend \(String\) -> String\? = \{ "([^"]*)"', body)
    assert stubs, "no fetch stub found to check; the test file was rewritten"
    for value in stubs:
        assert not value.strip().startswith("{"), (
            "a fetch stub returns " + value + ", which is a provider body. The seam's result goes "
            "into ProfileCountry.normalize, which wants a bare country code, so the stub yields "
            "null and the test fails exactly like the blocked-provider bug it is looking for.")


def check_the_tunnel_route_is_injectable_and_testable():
    """The route that fixes the flag had no test, and the first version of it broke seven.

    Reading the app's HTTP port goes through MMKV, which throws IllegalStateException before
    initialize() in a unit test, so ServerCountryLookup could not be constructed at all: every
    lookup test failed on the line before the one it was written to check. The fix is the same seam
    resolveDns and fetch already use, and it covers the credentials too, which are also MMKV.

    The assertions that matter are that the port is actually consulted, that the proxy is the
    loopback port rather than a direct route, and that the provider is still asked about the row's
    own address -- the tunnel is the route, not the question. Without that last one, a row's flag
    would report the selected server's exit.
    """
    country = (APP / "src/main/java/com/v2ray/ang/handler/ServerCountryLookup.kt").read_text("utf-8")
    ctor = country[country.index("class ServerCountryLookup("):]
    ctor = ctor[:ctor.index(") : Closeable")]
    for seam in ("tunnelPort", "tunnelUser", "tunnelPassword"):
        assert seam in ctor, (
            seam + " is not a constructor seam, so reading it reaches MMKV and every lookup test "
            "dies on MMKV.initialize() before it can assert anything")
    # Nothing on this class may read the settings store at all, and the seams must default to no
    # tunnel rather than to the store: a default that reads a global made twelve tests fail on
    # MMKV.initialize() twice, and a test that constructs the lookup without thinking about the
    # route would keep inheriting that dependency.
    assert "SettingsManager." not in country, (
        "the country lookup reads the settings store itself, so constructing it needs Android and "
        "every unit test that builds it dies before reaching its own assertion")
    ctor_txt = ctor
    for seam in ("tunnelPort", "tunnelUser", "tunnelPassword"):
        line = [l for l in ctor_txt.splitlines() if seam in l]
        assert line and "null" in line[0], (
            seam + " defaults to something other than no tunnel (" + (line[0].strip() if line else "missing")
            + "), so a construction that does not wire it silently reads the settings store")
    # The production caller has to do the wiring, or the fix is a no-op in the field: the flag would
    # go back to the direct route and the panel's DE would stand alone again.
    vm = (APP / "src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt").read_text("utf-8")
    wiring = vm[vm.index("private val serverCountries"):]
    wiring = wiring[:wiring.index("private val serverFlags")]   # the whole declaration
    for seam in ("tunnelPort", "tunnelUser", "tunnelPassword"):
        assert seam in wiring, (
            "the ViewModel no longer wires " + seam + ", so the lookup asks the provider directly "
            "again and no row gets a flag -- the blank page returns with no failing test")
    tests = TEST / "com/v2ray/ang/handler/CountryTunnelRouteTest.kt"
    assert tests.exists() and "class CountryTunnelRouteTest {" in tests.read_text("utf-8"), (
        "no test class covers the tunnel route, so it can be renamed or deleted silently -- the "
        "route is the whole fix and nothing else asserts it")
    body = tests.read_text("utf-8")
    for name, why in (
        ("theTunnelPortIsConsulted", "nothing checks that the port is read at all"),
        ("aTunnelOnALoopbackPortDoesNotChangeWhichAddressIsAskedAbout", "nothing checks that the route does not change the question"),
        ("theRowFlagStillComesFromTheRowAndNotFromTheExit", "nothing checks that a row's flag is its own country"),
    ):
        assert name in body, why + " (" + name + ")"


def check_the_country_lookup_client_is_a_valid_read_write_property():
    """A read-write property cannot delegate to a Lazy, and the failure is a compile error.

    Routing the lookup through the tunnel needs the client swapped at runtime, so the client is a
    var. It was first written as `private var client by clientHolder` over a `lazy {}` holding a
    built client, which does not compile -- Lazy has no setValue -- and because the property never
    got its real type, every call on it resolved against Proxy instead: `fun proxy(): Proxy?` is
    deprecated in favour of a val, `Too many arguments for 'fun proxy()'`, `Unresolved reference
    'build' on receiver of type 'Proxy?'`. Six errors in one build, all from one line.

    close() had the same shape: it guarded on `clientHolder.isInitialized()`, which no longer
    exists. And a closed lookup must not rebuild a client, because close() has already shut the
    executor down.
    """
    country = (APP / "src/main/java/com/v2ray/ang/handler/ServerCountryLookup.kt").read_text("utf-8")
    assert "clientHolder" not in country, (
        "the country lookup still holds its client behind a Lazy; a var cannot delegate to one, so "
        "this does not compile and every call on client resolves against the wrong type")
    m = re.search(r"private var client:\s*([\w<>?. ]+)\s*=", country)
    assert m, "the country lookup has no read-write client to swap when the route changes"
    assert "OkHttpClient" in m.group(1), (
        "the client property is typed " + m.group(1) + ", so the tunnel branch assigns a "
        "Proxy where an OkHttpClient is expected")
    assert "by clientHolder" not in country, (
        "the client is delegated to a Lazy, which cannot back a var: Lazy has no setValue")
    # The builder has to exist as a function, since the proxy is chosen per build.
    assert "newClientBuilder()" in country, (
        "the client is no longer built through a builder function, so the tunnel branch and the "
        "direct branch can drift apart in timeouts")
    closes = country[country.index("override fun close()"):]
    closes = closes[:closes.index("\n    }")]
    assert "closed = true" in closes, "close() does not mark the lookup closed"
    assert "if (closed) return" in country, (
        "a closed lookup still rebuilds its client, on an executor close() already shut down")
    # And every builder call must actually build, so no branch returns a builder where a client
    # is expected.
    for line in country.splitlines():
        if "newClientBuilder()" in line and "=" in line and "fun " not in line:
            assert ".build()" in line, (
                "the client is assigned " + line.strip() + " without building it, so the request "
                "path holds an OkHttpClient.Builder where it needs a client")


def check_flag_diagnostics_use_a_level_that_survives_the_default():
    """LogUtil's default level is "warning", so LogUtil.i is dropped before it reaches logcat.

    Eight releases carried diagnostics written with LogUtil.i. The strings were in the APK, the app
    was alive, and logcat returned nothing at all -- and that empty log was read as "the code never
    ran" every single time, which sent each fix further down a pipeline that had not been disproven.
    The one fact that would have settled it, that the default level discards INFO, is a property of
    the logging utility rather than of the flag code, so nothing about the flag path revealed it.

    The rule is now checked against the level LogUtil actually defaults to, so a diagnostic cannot
    be added back at a level that a default install will throw away.
    """
    log = (APP / "src/main/java/com/v2ray/ang/util/LogUtil.kt").read_text("utf-8")
    m = re.search(r'DEFAULT_LEVEL\s*=\s*"(\w+)"', log)
    assert m, "the default log level could not be read, so this guard cannot do its job"

    default = m.group(1).upper().replace("WARNING", "WARN")
    # Log.VERBOSE 2, DEBUG 3, INFO 4, WARN 5, ERROR 6, ASSERT 7
    floor = {"VERBOSE": 2, "DEBUG": 3, "INFO": 4, "WARN": 5, "ERROR": 6, "ASSERT": 7}[default]
    vm = (APP / "src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt").read_text("utf-8")
    for line in ("flag init: collectors starting", "flag init: country walk", "flag walk"):
        at = vm.index(line)
        call = vm[:at].rsplit("LogUtil.", 1)[1][0]
        level = {"v": 2, "d": 3, "i": 4, "w": 5, "e": 6}[call]
        assert level >= floor, (
            "the diagnostic \"" + line + "\" is logged at " + call + " but the default level is \""
            + default + "\", so a stock install discards it and the line can never be observed. A "
            "diagnostic nobody can see is the failure mode this guard exists to prevent: an empty "
            "logcat read as untested code.")


def check_the_flag_path_says_where_it_stops():
    """Seven releases of fixes were reasoned about a path that never ran.

    The strings were in the APK and the app was alive and logcat returned nothing at all, which
    means the walk was never entered. Every previous diagnosis -- a blocked provider, a dead route,
    a wrong parser, a private address -- sits downstream of the first line of the walk, so all of
    them were unobservable.

    The chain is now logged in the order it happens, and the first line missing names the culprit:
    the ViewModel was built, each collector started, the row list and the filtered target count,
    the walk started, the lookup's answer. The two collector-entry lines matter most: without
    them a reader has to guess whether the pipeline never began or began and produced nothing.

    refreshFlags had no recorder at all, so a manual ping -- the one action a user takes when they
    expect a row to change -- was silent even after the walk was instrumented.
    """
    vm = (APP / "src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt").read_text("utf-8")
    for line, why in (
        ("flag init: collectors starting",
         "nothing records whether the ViewModel built the flag collectors at all, which is the "
         "first question when the whole path is silent"),
        ("flag init: verdict walk",
         "nothing records that the verdict collector started, so a group that never emits and a "
         "collector that never ran look the same"),
        ("flag init: country walk",
         "nothing records that the country collector started, which is the one that has to fire "
         "for a row to get a flag"),
        ("country targets=",
         "nothing records how many targets the country walk was handed, so an empty row list and a "
         "row list the filter emptied are indistinguishable"),
        ("verdict targets=",
         "nothing records how many targets the verdict walk was handed"),
    ):
        assert line in vm, why + " (" + line + ")"
    for walk in ("refresh-verdict", "refresh-country", "verdict", "country"):
        assert 'onWalk = walkLog("%s")' % walk in vm, (
            "the walk labelled " + walk + " is not recorded, so a ping that changed nothing is "
            "silent -- and a ping is exactly when a user expects a row to change")
    # The row count has to be logged next to the target count, or "0 targets" has no explanation.
    assert vm.count("rows=") >= 2, (
        "the target counts are logged without the row counts, so a zero target count cannot be "
        "attributed to an empty list or to the filter")


def check_the_flag_path_records_without_depending_on_the_settings_store():
    """The line added to find the missing flag became the reason eleven tests could not run.

    LogUtil reads its level from the settings store, so a LogUtil call anywhere on a path a unit
    test executes fails with IllegalStateException: You should Call MMKV.initialize() first -- and it
    fails before the test reaches its own assertion, so the report is about MMKV and says nothing
    about the flag. That is twice now on this class, the first time through the tunnel port and the
    second time through the diagnostic.

    So the rule is the seam: the lookup and the walk report through a callback the caller owns, the
    default is silence, and the ViewModel -- which runs on a device, where the settings store
    exists -- is what turns it into a log line. Both the port and the diagnostic follow the same
    shape, so neither can reintroduce the crash.
    """
    country = (APP / "src/main/java/com/v2ray/ang/handler/ServerCountryLookup.kt").read_text("utf-8")
    batch = (APP / "src/main/java/com/v2ray/ang/ui/main/FlagBatch.kt").read_text("utf-8")
    vm = (APP / "src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt").read_text("utf-8")

    for name, src in (("ServerCountryLookup", country), ("FlagBatch", batch)):
        body = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
        body = re.sub(r"//[^\n]*", " ", body)
        assert "LogUtil" not in body, (
            name + " calls LogUtil, which reads the log level from the settings store; every unit "
            "test that runs this code then dies on MMKV.initialize() before its own assertion. The "
            "diagnostic has to go through a seam the caller owns.")
        assert "MmkvManager" not in body and "SettingsManager" not in body, (
            name + " reaches the settings store directly, so constructing or running it needs "
            "Android and the unit tests for the flag path cannot run")

    # Both seams exist, default to silence, and the ViewModel supplies them.
    assert "onOutcome" in country and "onOutcome: ((CountryOutcome) -> Unit)? = null" in country, (
        "the lookup no longer reports its outcome through a seam that defaults to silence, so a "
        "diagnostic has to be written inline and takes the tests down with it")
    assert "onWalk" in batch and "onWalk: ((started: Boolean, targets: Int, force: Boolean) -> Unit)? = null" in batch, (
        "the walk no longer reports through a seam that defaults to silence")
    wiring = vm[vm.index("private val serverCountries"):]
    wiring = wiring[:wiring.index("private val serverFlags")]
    assert "onOutcome" in wiring, (
        "the ViewModel no longer turns the lookup's outcome into a log line, so the flag goes "
        "missing again with nothing to see: no row asked, no provider tried, no reason")
    assert 'onWalk = walkLog("verdict")' in vm and 'onWalk = walkLog("country")' in vm, (
        "a walk is no longer recorded, so a walk that never starts and a walk whose lookups all "
        "come back empty are the same silence")

    # The outcome must carry what distinguishes those two, and no hostname.
    outcome = country[country.index("data class CountryOutcome"):]
    outcome = outcome[:outcome.index(")")] if ")" in outcome else outcome
    for field in ("hit", "viaTunnel", "keyLength"):
        assert field in outcome, (
            "the outcome no longer carries " + field + ", so a blocked provider and a row that was "
            "never asked report the same thing")
    assert "key" not in outcome.replace("keyLength", ""), (
        "the outcome carries the address itself, which the root guide forbids logging")


def check_the_flag_lookup_asks_through_the_tunnel():
    """The (DE) in the connection panel and the flag on a row are two different questions.

    The panel asks the provider about nobody: SpeedtestManager.getRemoteIPInfo() calls
    api.ip.sb/geoip with no address, through the app's loopback HTTP port, and the provider answers
    with the exit the tunnel currently has. It works, and it is the one place the app already shows
    a country.

    A row flag asks about one address. This lookup was pinned to Proxy.NO_PROXY, so it asked from
    outside the tunnel, and on a network where the providers are blocked that is a request into a
    black hole: null for every row, no flag anywhere, while the panel two inches above showed DE.

    So the route changes and the question does not. A row flag must never be taken from the
    tunnel's own exit, because that is the selected server's country, not the row's, and every
    other row would silently inherit it.
    """
    country = (APP / "src/main/java/com/v2ray/ang/handler/ServerCountryLookup.kt").read_text("utf-8")
    # Proxy.NO_PROXY is only allowed as the disconnected fallback. What must not exist any more is
    # the client being built with it unconditionally, which is the bug: every row asked from
    # outside the tunnel, and outside the tunnel the providers are blocked.
    # Proxy.NO_PROXY is the disconnected fallback. What must not exist is the initial client
    # being the only one, with the tunnel branch missing: that is the state the panel's DE proves
    # was wrong, since the panel asks through the tunnel and every row asked without it.
    initial = re.search(r"private var client:[^\n]*", country)
    assert initial, "the country lookup has no client property"
    assert "Proxy.NO_PROXY" in initial.group(0), (
        "with no tunnel the lookup has to fall back to the direct route, or it has no client at all "
        "before the first connection")
    assert "routeThroughTunnelIfUp" in country, "the country lookup never re-routes"
    assert "tunnelProxy" in country, "the country lookup has no tunnel route to switch to"
    res = country[country.index("suspend fun resolve(address: String?): String? {"):]
    res = res[:res.index("\n    }")]
    assert "routeThroughTunnelIfUp()" in res, (
        "resolve() never re-routes, so a client built while disconnected keeps asking from "
        "outside the tunnel for the life of the ViewModel and no row gets a flag")
    # The port is read by the ViewModel, not by the lookup: reading it inside the class would put
    # a settings-store read on its construction. The wiring is checked above and here.
    vm_txt = (APP / "src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt").read_text("utf-8")
    assert "SettingsManager.getHttpPort()" in vm_txt, (
        "nothing reads the app's own HTTP port, so the lookup cannot ask through the tunnel the way "
        "the connection panel does")
    # The per-address question must survive the route change: every provider has to ask about an
    # address, because a provider that asks about nobody returns the tunnel's own exit and every
    # row would inherit the selected server's country.
    eps = re.search(r"COUNTRY_ENDPOINTS = listOf\((.*?)\n        \)", country, re.S)
    assert eps, "the country providers could not be read"
    for endpoint in re.findall(r'"([^"]+)"', eps.group(1)):
        assert "{ip}" in endpoint, (
            "a country provider (" + endpoint + ") asks about nobody, so it returns the tunnel's "
            "own exit and every row would show the selected server's country")
    # And the same route has to be available to the panel's own lookup, or the two disagree.
    speed = (APP / "src/main/java/com/v2ray/ang/handler/SpeedtestManager.kt").read_text("utf-8")
    assert "httpPort = httpPort" in speed and "IP_API_URL" in speed, (
        "the connection panel no longer asks through the tunnel, so its country and the row flags "
        "would come from different egresses and could disagree on the same server")


def check_a_flag_walk_survives_its_own_publish():
    """A walk that publishes into the state it derives its work from cancels itself.

    Both walks were `collectLatest` blocks over a flow mapped from the row list, and publishing a
    verdict edits that row list. The first row answered, its own answer produced a new emission,
    and collectLatest tore the pass down with the rest of the page unasked: the page showed a
    verdict on one row and nothing on the others, and the country flag never appeared at all. The
    walk has to run in a job the collector does not own.

    The failure is invisible in a diff because the code reads correctly, and the visible symptom
    (a blank flag) points at the provider, the parser and the row model, none of which were wrong.
    """
    vm = (APP / "src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt").read_text("utf-8")
    slots = set()
    for name in ("collectServerFlags", "collectServerCountries"):
        i = vm.index("private fun " + name)
        j = vm.index("\n    private fun", i + 10)
        body = vm[i:j]
        assert "collectLatest" in body or "map" in body, name + " no longer watches the row list"
        assert "viewModelScope.launch" in body, (
            name + " runs its walk inside the collector, so collectLatest cancels it the moment "
            "its first publish changes the row list it was derived from")
        assert ".join()" not in body, (
            name + " joins the walk, which puts it back under collectLatest and restores the "
            "cancellation; the job only has to be registered so a group switch can cancel it")
        assert "toList()" in body, (
            name + " passes the derived list straight through; a snapshot stops a later emission "
            "from mutating the walk's own target list underneath it")
        m = re.search(r"(groupPassJobs|groupCountryJobs)\[groupId\] = pass", body)
        assert m, name + " registers no job, so a group switch cannot cancel its walk"
        slot = m.group(1)
        # Cancel through the same map it registers into; two maps for one walk cancels nothing.
        cancels = re.findall(r"(groupPassJobs|groupCountryJobs)\[groupId\]\?\.cancel\(\)", body)
        assert cancels and set(cancels) == {slot}, (
            name + " cancels through " + str(sorted(set(cancels))) + " but registers into " + slot
            + ", so the previous walk of that kind is never cancelled")
        slots.add(slot)
        assert not re.search(r"\bfor \(", body), (
            name + " walks its targets in a plain for loop, so a page of eight pays the pacing gap "
            "eight times and the last row is seconds behind the first")
    assert len(slots) == 2, (
        "both walks share one job slot (" + str(sorted(slots)) + "), so whichever collector emits "
        "last cancels the other and the page shows a verdict with no country, or the reverse")


def check_the_documented_colour_exceptions_hold():
    """Four colours were deliberately taken back out of the palette sweep, and each has a reason.

    A sweep that maps everything to five anchors will happily take the one thing that must not move.
    These are the four the user sent a screenshot for: the "not flagged" verdict reads as a
    verdict only in its own green, the row separators were carrying the original greys, and the
    selected row and the group tab are gold. The gold is the palette hue darkened to 3:1, because
    FED766 on white is 1.4:1 and a 4dp bar or a 2dp underline would simply not be visible.
    """
    theme = (APP / "src/main/java/com/v2ray/ang/ui/compose/Theme.kt").read_text("utf-8")
    m = re.search(r"dividerColorLight = Color\(0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})\)", theme)
    assert m, "the light row separator lost its constant"
    assert m.group(1).upper() == "E0E0E0", (
        "the light row separator is #" + m.group(1) + " again; it was E0E0E0 before the palette "
        "sweep and a hairline has to stay a hairline")
    m = re.search(r"dividerColorDark = Color\(0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})\)", theme)
    assert m and m.group(1).upper() == "424242", "the dark row separator left 424242"

    risk = (APP / "src/main/java/com/v2ray/ang/ui/main/RiskBadge.kt").read_text("utf-8")
    m = re.search(r"RiskLevel\.SAFE -> Color\(0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})\)", risk)
    assert m, "the safe verdict lost its colour"
    assert m.group(1).upper() == "009966", (
        "the \"not flagged\" verdict is #" + m.group(1) + " again. It is the latency green, not the "
        "palette teal: teal is the accent and the connect button, so a clean verdict in it read as "
        "another accent rather than as an answer.")

    # Both gold markers come from one constant, and it has to be the darkened gold, not FED766.
    pager = (APP / "src/main/java/com/v2ray/ang/ui/main/MainServerPager.kt").read_text("utf-8")
    tab = (APP / "src/main/java/com/v2ray/ang/ui/main/MainGroupTab.kt").read_text("utf-8")
    gold = "AD9245"
    assert re.search(r"SelectedRowGold = Color\(0x[0-9A-Fa-f]{2}" + gold + r"\)", pager), (
        "the selected row marker is not the palette gold; it must be #" + gold)
    assert ".background(SelectedRowGold)" in pager, (
        "the selected row marker is not using the gold; a 4dp bar needs 3:1 on white to be seen")
    assert re.search(r"GroupIndicatorGold = Color\(0x[0-9A-Fa-f]{2}" + gold + r"\)", tab), (
        "the group tab underline is not the palette gold; it must be #" + gold)
    # The lambda holds a Modifier.tabIndicatorOffset(...) with its own parentheses, so [^)]* stops
    # short. Bound the search to the SecondaryIndicator call itself.
    ind = re.search(r"SecondaryIndicator\(.*?\n\s*\)\n", tab, re.S)
    m = re.search(r"color = (\w+)", ind.group(0)) if ind else None
    assert m and m.group(1) == "GroupIndicatorGold", (
        "the group tab underline is drawn with " + (m.group(1) if m else "nothing")
        + " rather than the gold; colorScheme.secondary put slate there, which read as disabled")
    # The palette gold itself is what these are darkened from, so keep them in step.
    assert "FED766" in theme, "the gold anchor is gone from the theme, so the darkened gold is orphaned"


def check_the_launcher_wordmark_is_teal_and_20_percent_smaller():
    """The wordmark was asked to shrink by a fifth and take the palette teal.

    Both are invisible in a diff of the rendered PNG and easy to undo by hand, so the generator
    is pinned instead of the artwork: the ink must be the teal anchor, and the legend must stay at
    0.8 of the size it was. The committed PNGs are checked too, so a regenerated-then-reverted
    asset cannot pass.
    """
    src = (ROOT / "tools/make_icons.py").read_text("utf-8")
    m = re.search(r"INK\s*=\s*'#([0-9A-Fa-f]{6})'", src)
    assert m, "make_icons.py no longer names the wordmark ink"
    ink = m.group(1).upper()
    assert ink == "009FB7", "the launcher wordmark left the palette: #" + ink
    for name, old in (("LEGEND_SIZE", 0.29), ("FOREGROUND_SIZE", 0.24)):
        mm = re.search(name + r"\s*=\s*([0-9.]+)", src)
        assert mm, name + " is gone from make_icons.py"
        got = float(mm.group(1))
        want = round(old * 0.8, 4)
        assert abs(got - want) < 1e-6, (
            name + " is " + str(got) + ", which is not 20% smaller than the " + str(old) + " it was")
    assert "fill='white'" not in src and 'fill="white"' not in src, (
        "a wordmark is still painted white, which is not the palette teal")
    try:
        from PIL import Image
    except ImportError:
        return
    from collections import Counter
    for rel in ("mipmap-xxxhdpi/ic_launcher.png", "drawable/ic_ning_logo.png",
                "mipmap-xhdpi/ic_banner.png"):
        im = Image.open(APP / ("src/main/res/" + rel)).convert("RGB")
        # getdata() is deprecated in Pillow 14; get_flattened_data() is the replacement when the
        # installed Pillow has it, and the guard has to run on whatever the runner happens to have.
        flat = getattr(im, "get_flattened_data", None)
        c = Counter(flat() if flat else im.getdata())
        teal = sum(n for (r, g, b), n in c.items()
                   if abs(r - 0x00) + abs(g - 0x9F) + abs(b - 0xB7) < 40)
        bright = sum(n for (r, g, b), n in c.items() if r > 200 and g > 200 and b > 200)
        assert teal > 200, rel + " has almost no teal in it: " + str(teal) + " px"
        assert bright < teal, rel + " is still mostly white text: " + str(bright) + " vs " + str(teal)


def check_a_rewritten_function_keeps_its_helpers_and_returns():
    """Rewriting resolve() by hand dropped a helper and a return, and only the compiler noticed.

    The two-lock split is a small, local change; rewriting the whole function body around it is
    not, and it cost a release. Both failures are invisible to a text guard and cheap to catch:
    every identifier the file still calls must still be defined, and a block-bodied resolve() must
    return on every path.
    """
    for name in ("ServerFlaggedLookup", "ServerCountryLookup"):
        src = (APP / ("src/main/java/com/v2ray/ang/handler/" + name + ".kt")).read_text("utf-8")
        defined = set()
        for pat in (r"private (?:suspend )?fun ([a-zA-Z_]\w*)",
                    r"private (?:suspend )?val ([a-zA-Z_]\w*)",
                    r"(?:data |sealed |enum )*class ([a-zA-Z_]\w*)",
                    r"object ([a-zA-Z_]\w*)",
                    r"interface ([a-zA-Z_]\w*)",
                    r"typealias ([a-zA-Z_]\w*)"):
            defined |= set(re.findall(pat, src))
        for imp in re.findall(r"import ([a-zA-Z_][\w.]*)", src):
            defined.add(imp.rsplit(".", 1)[-1])
        # Locals are declared in their own scope; a name bound by val/var/fun/param is fine.
        for pat in (r"\bval ([a-zA-Z_]\w*)", r"\bvar ([a-zA-Z_]\w*)", r"\bfun ([a-zA-Z_]\w*)",
                    r"([a-zA-Z_]\w*):\s", r"\bfor \(([a-zA-Z_]\w*)"):
            defined |= set(re.findall(pat, src))
        # Only unqualified calls can be a missing local helper; a call after a dot is a method,
        # and "val x: Int get() = ..." is a property accessor rather than a call.
        # Comments come out first: a doc line that mentions initialize() or build() is prose about
        # the Android or OkHttp call being described, and reading it as a call to a local that does
        # not exist is a false positive that hides the real one.
        body_wo_accessors = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
        body_wo_accessors = re.sub(r"//[^\n]*", " ", body_wo_accessors)
        body_wo_accessors = re.sub(r"\bget\(\)", "get_", body_wo_accessors)
        for called in re.findall(r"(?<![.\w])([a-zA-Z_]\w*)\(", body_wo_accessors):
            if called in _KEYWORDS or called in _IDIOMS:
                continue
            assert called in defined, (
                name + " calls " + called + "() but never defines it; a hand rewrite dropped it")
        fn = re.search(r"    suspend fun resolve[(].*?\n    \}", src, re.S)
        assert fn, name + " has no resolve() to check"
        body = fn.group(0)
        if not body.rstrip().endswith("}"):
            continue  # an expression body returns by construction
        lines = [l.strip() for l in body.rstrip().splitlines() if l.strip()]
        assert "return" in body, (
            name + ": resolve() is a block body that never returns, so it does not compile")
        assert lines[-2].startswith("return"), (
            name + ": the last statement of resolve() is " + lines[-2] + ", whose value is discarded")


def check_theme_pairs_stay_readable():
    """A remap that ignores contrast turns a colour into an unreadable one.

    Five colours is few enough that pairing is a real constraint, so the sweep has to check the
    result rather than assume the anchors work together. Red is exempt: it is a status colour with
    no palette member, so its pair is the closest the palette allows.
    """
    theme = (APP / "src/main/java/com/v2ray/ang/ui/compose/Theme.kt").read_text("utf-8")
    # Parsed line by line on purpose: a regex over the whole scheme block is brittle, because the
    # body is full of nested parentheses and a colour pattern matches happily across a boundary.
    schemes = {}
    current = None
    for line in theme.splitlines():
        stripped = line.strip()
        if stripped.startswith("private val LightColor"):
            current = "light"
        elif stripped.startswith("private val DarkColor"):
            current = "dark"
        elif stripped.startswith(")") or stripped.startswith("//") or stripped == "":
            if current is not None and stripped == ")":
                current = None
        if current:
            m = re.match(r"^    ([a-zA-Z]+) = Color\(0xFF([0-9A-Fa-f]{6})\),", line)
            if m:
                schemes.setdefault(current, {})[m.group(1)] = m.group(2)
    assert set(schemes) == {"light", "dark"}, "the light or dark scheme is missing"
    pairs = [("primary", "onPrimary"), ("primaryContainer", "onPrimaryContainer"),
             ("secondary", "onSecondary"), ("secondaryContainer", "onSecondaryContainer"),
             ("tertiary", "onTertiary"), ("tertiaryContainer", "onTertiaryContainer"),
             ("errorContainer", "onErrorContainer"), ("background", "onBackground"),
             ("surface", "onSurface"), ("surfaceVariant", "onSurfaceVariant")]
    for name in ("light", "dark"):
        cols = schemes[name]
        for bg, fg in pairs:
            if bg in cols and fg in cols:
                ratio = _contrast(cols[fg], cols[bg])
                assert ratio >= 4.5, (
                    name + ": " + fg + " on " + bg + " is " + ("%.1f" % ratio)
                    + ":1, which is unreadable at text size")


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
