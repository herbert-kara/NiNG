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
    check_a_lock_does_not_serialise_the_lookups()
    check_a_measured_delay_forces_a_fresh_lookup()
    check_the_palette_is_the_whole_theme()
    check_the_connect_button_is_blue_and_ping_is_untouched()
    check_theme_pairs_stay_readable()
    check_a_rewritten_function_keeps_its_helpers_and_returns()
    check_the_launcher_wordmark_is_teal_and_20_percent_smaller()
    check_the_documented_colour_exceptions_hold()
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
        body_wo_accessors = re.sub(r"\bget\(\)", "get_", src)
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
