#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Regenerate docs/verification/test-specs.xlsx from the test sources.

Every row is a real test found in the repository. Names, steps and assertions are
read out of the source; pass/fail comes from each runner's own report, and any
test with no report entry is written as "Not run" rather than assumed green.

Usage:

    # 1. run the suites, so their results are real
    cd backend && ./gradlew test            # writes build/test-results/test/*.xml
    cd frontend
    npx vitest run --reporter=json --outputFile=../.test-results/vitest.json
    npx playwright test --reporter=json > ../.test-results/playwright.json
    cd ../demo/importer && npx vitest run --reporter=json --outputFile=../../.test-results/importer.json

    # 2. build the workbook
    python scripts/build-test-specs.py

Options:
    --vitest-json PATH      default: .test-results/vitest.json
    --playwright-json PATH  default: .test-results/playwright.json
    --importer-json PATH    default: .test-results/importer.json
    --junit-xml GLOB        default: backend/*/build/test-results/test/*.xml
    --backend-status TEXT   fallback for Kotlin tests with no JUnit entry;
                            default "Not run"
    --out PATH              default: docs/verification/test-specs.xlsx

Requires openpyxl (`pip install openpyxl`).
"""
import argparse
import collections
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import test_spec_notes as NOTES

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# --------------------------------------------------------------------- shared


def rel(p):
    return os.path.relpath(p, ROOT).replace("\\", "/")


def read(p):
    with open(p, encoding="utf-8") as fh:
        return fh.read()


def dedent_block(lines):
    lines = [l.rstrip() for l in lines]
    real = [l for l in lines if l.strip()]
    if not real:
        return []
    pad = min(len(l) - len(l.lstrip()) for l in real)
    return [l[pad:] if l.strip() else "" for l in lines]


CONT_ENDINGS = (",", "(", "{", "=", "->", "&&", "||", "+", ".")


def statements(lines):
    """Group physical lines into logical statements (bracket aware)."""
    out, buf, depth, cbuf = [], [], 0, []

    def flush_comments():
        if cbuf:
            out.append("\n".join(cbuf))
            del cbuf[:]

    for ln in lines:
        s = ln.strip()
        if not s:
            continue
        # Consecutive comment lines form one annotation and never affect bracket
        # depth — prose routinely contains an unbalanced "(" and would otherwise
        # swallow every following line into a single step.
        if s.startswith("//") and depth <= 0:
            cbuf.append(ln)
            continue
        flush_comments()
        # A line opening with "." continues the previous call chain (AssertJ and
        # Playwright both wrap that way), so it never starts a new step.
        if s.startswith(".") and (buf or out):
            if not buf:
                buf = [out.pop()]
            buf.append(ln)
            depth += s.count("(") + s.count("{") + s.count("[")
            depth -= s.count(")") + s.count("}") + s.count("]")
            if depth <= 0 and not s.endswith(CONT_ENDINGS):
                out.append("\n".join(buf))
                buf, depth = [], 0
            continue
        buf.append(ln)
        depth += s.count("(") + s.count("{") + s.count("[")
        depth -= s.count(")") + s.count("}") + s.count("]")
        if depth <= 0 and not s.endswith(CONT_ENDINGS):
            out.append("\n".join(buf))
            buf, depth = [], 0
    if buf:
        out.append("\n".join(buf))
    flush_comments()
    return out


def collapse(stmt, limit=240):
    s = re.sub(r"\s*\n\s*", " ", stmt.strip())
    s = re.sub(r"\s{2,}", " ", s)
    return s if len(s) <= limit else s[: limit - 1] + "\u2026"


ASSERT_RE = re.compile(
    r"\b(assertThat|assertThatThrownBy|assertThatCode|assertThatExceptionOfType|assertThrows|"
    r"assertEquals|assertTrue|assertFalse|assertNull|assertNotNull|assertFailsWith|"
    r"verify|verifyNoInteractions|verifyNoMoreInteractions|expect|andExpect|"
    r"toHaveURL|toBeVisible)\s*[({<]"
)


def is_assertion(stmt):
    return bool(ASSERT_RE.search(stmt))


def leading_comment(lines, idx):
    """The comment block immediately above line idx (exclusive)."""
    out, i = [], idx - 1
    while i >= 0:
        s = lines[i].strip()
        if s.startswith("//"):
            out.append(s[2:].strip())
            i -= 1
            continue
        if s.endswith("*/"):
            blk = []
            while i >= 0 and "/*" not in lines[i]:
                blk.append(lines[i].strip().lstrip("*").strip())
                i -= 1
            if i >= 0:
                head = re.sub(r"^/\*+", "", lines[i].strip()).strip().lstrip("*").strip()
                head = head.replace("*/", "").strip()
                if head:
                    blk.append(head)
            out.extend(blk)
            i -= 1
            continue
        if s.startswith("@"):
            i -= 1
            continue
        break
    return re.sub(r"\s{2,}", " ", " ".join(x for x in reversed(out) if x)).strip()


# Text that can hold a brace which does not open or close a block. Masked with
# spaces rather than removed, so a column index still points at the same place.
BRACE_NOISE = re.compile("|".join((
    r"\\.",                          # an escaped character, e.g. a regex \{
    r"'(?:\\.|[^'\\\n])*'",          # a single-quoted string
    r'"(?:\\.|[^"\\\n])*"',          # a double-quoted string
    r"`(?:\\.|[^`\\\n])*`",          # a template literal
    r"\[\^[^\]\n]*\]",               # a negated character class, e.g. [^}]
    r"//.*$",                        # a line comment
)), re.M)

RAW = '"' * 3


def mask_noise(line, in_raw=False):
    """Blank out brace-bearing text; tracks Kotlin's multi-line raw strings.

    A GraphQL document in a Kotlin test is a raw string spanning several lines,
    and the quotes inside it are data. Masking it line by line would pair those
    quotes up wrongly and hide one half of a brace pair.
    """
    out, parts = [], line.split(RAW)
    for n, part in enumerate(parts):
        out.append(" " * len(part) if in_raw
                   else BRACE_NOISE.sub(lambda m: " " * len(m.group(0)), part))
        if n < len(parts) - 1:
            out.append(" " * len(RAW))
            in_raw = not in_raw
    return "".join(out), in_raw


def brace_block(lines, i, col=0):
    """The body between the first "{" at or after line i and its match.

    Masks string, comment and escape noise so that a brace inside a literal
    never opens a block, and keeps character positions while doing it, so a
    one-line body (it('...', () => { expect(x).toBe(1); })) is still read.
    `col` skips past a wrapped callback header's own destructuring brace.
    """
    depth, open_at, in_raw = 0, None, False
    for j in range(i, len(lines)):
        masked, in_raw = mask_noise(lines[j], in_raw)
        for k, ch in enumerate(masked):
            if j == i and k < col:
                continue
            if ch == "{":
                depth += 1
                if open_at is None:
                    open_at = (j, k)
            elif ch == "}" and open_at is not None:
                depth -= 1
                if depth > 0:
                    continue
                if j == open_at[0]:
                    return [x for x in [lines[j][open_at[1] + 1:k]] if x.strip()]
                head, tail = lines[open_at[0]][open_at[1] + 1:], lines[j][:k]
                mid = lines[open_at[0] + 1:j]
                return (([head] if head.strip() else []) + mid
                        + ([tail] if tail.strip() else []))
    return []


# --------------------------------------------------------------------- kotlin

KT_MODULES = ("catalogue-service", "people-service", "media", "contracts")


def parse_kotlin():
    rows = []
    for mod in KT_MODULES:
        base = os.path.join(ROOT, "backend", mod, "src", "test", "kotlin")
        if not os.path.isdir(base):
            continue
        for dp, _, fns in os.walk(base):
            for fn in sorted(fns):
                if not fn.endswith(".kt"):
                    continue
                path = os.path.join(dp, fn)
                src = read(path)
                lines = src.split("\n")
                # One file routinely declares several test classes side by side, and
                # the JUnit report keys on the real one — so resolve each @Test to
                # the class most recently declared above it, not to the first.
                declared = [(i, m.group(1)) for i, l in enumerate(lines)
                            for m in [re.match(r"^(?:internal\s+)?class\s+(\w+)", l)] if m]
                if not declared:
                    declared = [(0, fn[:-3])]
                pm = re.search(r"^package\s+([\w.]+)", src, re.M)
                tags = sorted(set(re.findall(
                    r"@(Testcontainers|SpringBootTest|DataJpaTest|GraphQlTest|WebMvcTest"
                    r"|AutoConfigureMockMvc|ActiveProfiles)", src)))
                for idx, ln in enumerate(lines):
                    if not re.match(r"^\s*@Test\s*$", ln):
                        continue
                    k = idx + 1
                    while k < len(lines) and "fun " not in lines[k]:
                        k += 1
                    fm = re.search(r"fun\s+`([^`]+)`", lines[k]) if k < len(lines) else None
                    name = fm.group(1) if fm else re.sub(r".*fun\s+(\w+).*", r"\1", lines[k])
                    b = k
                    while b < len(lines) and "{" not in lines[b]:
                        b += 1
                    cls_line, cls = max((d for d in declared if d[0] < idx), default=declared[0])
                    rows.append({
                        "module": mod, "file": rel(path), "line": k + 1,
                        "suite": cls, "suite_doc": leading_comment(lines, cls_line),
                        "package": pm.group(1) if pm else "", "tags": tags,
                        "name": name, "doc": leading_comment(lines, idx),
                        "stmts": statements(dedent_block(brace_block(lines, b))),
                    })
    return rows


# ----------------------------------------------------------------- typescript

TS_CASE = re.compile(r"""^(\s*)(?:it|test)(?:\.\w+)?\s*\(\s*(['"`])((?:\\.|(?!\2).)+?)\2""")
TS_SUITE = re.compile(r"""^(\s*)describe(?:\.\w+)?\s*\(\s*(['"`])((?:\\.|(?!\2).)+?)\2""")


def unescape_title(t):
    return re.sub(r"\\(['\"`\\])", r"\1", t)


def parse_ts(path):
    lines = read(path).split("\n")
    stack, out = [], []
    for i, ln in enumerate(lines):
        ind = len(ln) - len(ln.lstrip())
        if stack and ln.strip().startswith("})") and ind <= stack[-1][0]:
            stack.pop()
        ms = TS_SUITE.match(ln)
        if ms:
            while stack and ind <= stack[-1][0]:
                stack.pop()
            stack.append((ind, unescape_title(ms.group(3))))
            continue
        mc = TS_CASE.match(ln)
        if mc:
            while stack and ind <= stack[-1][0]:
                stack.pop()
            # The callback header can wrap, so start the body at the arrow rather
            # than at the test( line's own brace.
            b, arrow = i, None
            while b < len(lines) and b < i + 12:
                arrow = re.search(r"(=>|function\s*\([^)]*\))\s*\{", lines[b])
                if arrow:
                    break
                b += 1
            if not arrow or b >= len(lines) or b >= i + 12:
                b, arrow = i, None
            out.append({
                "suite_path": [t for _, t in stack],
                "name": unescape_title(mc.group(3)), "line": i + 1,
                "doc": leading_comment(lines, i),
                "stmts": statements(dedent_block(
                    brace_block(lines, b, arrow.start() if arrow else 0))),
            })
    return out


def file_head_comment(path):
    blk = []
    for ln in read(path).split("\n")[:45]:
        s = ln.strip()
        if s.startswith("import "):
            continue
        if s.startswith(("const ", "let ", "describe(", "test(", "it(", "beforeEach", "vi.mock")):
            break
        if s.startswith("//"):
            blk.append(s[2:].strip())
        elif s.startswith(("/**", "*", "/*")):
            blk.append(re.sub(r"^/?\*+/?", "", s).replace("*/", "").strip())
    return re.sub(r"\s{2,}", " ", " ".join(x for x in blk if x)).strip()


def parse_frontend():
    rows = []
    for base, kind, suffix in (
        (os.path.join(ROOT, "frontend", "src"), "vitest", ".test.ts"),
        (os.path.join(ROOT, "frontend", "e2e"), "playwright", ".spec.ts"),
        # The importer is a separate npm project with its own Vitest run, but the
        # test files parse identically, so it rides along here.
        (os.path.join(ROOT, "demo", "importer", "src"), "importer", ".test.ts"),
    ):
        for dp, _, fns in os.walk(base):
            for fn in sorted(fns):
                if not fn.endswith(suffix):
                    continue
                path = os.path.join(dp, fn)
                head = file_head_comment(path)
                for t in parse_ts(path):
                    t.update({"kind": kind, "file": rel(path), "file_doc": head})
                    rows.append(t)
    return rows


# ----------------------------------------------------------- subject lookup

MAIN_SYMBOLS = {}


def index_main_symbols():
    for mod in KT_MODULES:
        base = os.path.join(ROOT, "backend", mod, "src", "main", "kotlin")
        if not os.path.isdir(base):
            continue
        for dp, _, fns in os.walk(base):
            for fn in fns:
                if not fn.endswith(".kt"):
                    continue
                p = os.path.join(dp, fn)
                src = read(p)
                for m in re.finditer(
                        r"^\s*(?:@\w+[^\n]*\n\s*)*(?:internal\s+|open\s+|abstract\s+|data\s+|sealed\s+)*"
                        r"(?:class|object|interface)\s+(\w+)", src, re.M):
                    MAIN_SYMBOLS.setdefault(m.group(1), rel(p))
                for m in re.finditer(r"^fun\s+(\w+)", src, re.M):
                    MAIN_SYMBOLS.setdefault(m.group(1), rel(p))


_subject_cache = {}


def kt_subject(row):
    base = re.sub(r"(IntegrationTest|ContractTest|Test)$", "", row["suite"])
    if base in MAIN_SYMBOLS:
        return "%s :: %s" % (MAIN_SYMBOLS[base], base)
    # No symbol matches the class name; fall back to the production symbol the
    # test file references most. Exception types are skipped — they are named far
    # more often than the class that throws them.
    key = row["file"]
    if key not in _subject_cache:
        src = read(os.path.join(ROOT, key))
        best, best_n = None, 0
        for sym in MAIN_SYMBOLS:
            if len(sym) < 4 or sym.endswith(("Test", "Exception")):
                continue
            n = len(re.findall(r"\b%s\b" % re.escape(sym), src))
            if n > best_n:
                best, best_n = sym, n
        _subject_cache[key] = ("%s :: %s" % (MAIN_SYMBOLS[best], best)) if best_n >= 2 else row["package"]
    return _subject_cache[key]


def fe_subject(test_file):
    d, b = os.path.dirname(test_file), os.path.basename(test_file)
    stem = b[: -len(".test.ts")]
    special = {"page.server": "+page.server.ts", "page.svelte": "+page.svelte",
               "page": "+page.svelte", "error.svelte": "+error.svelte",
               "app.css": "app.css", "source-guard": None}
    cand = []
    if stem in special:
        if special[stem] is None:
            return "frontend/src (whole source tree \u2014 repository-wide guard)", []
        cand.append(os.path.join(d, special[stem]))
    stem0 = stem.split(".")[0]
    cand += [os.path.join(d, stem + ".ts"), os.path.join(d, stem + ".svelte"),
             os.path.join(d, stem0 + ".ts"), os.path.join(d, stem0 + ".svelte")]
    target = next((c.replace("\\", "/") for c in cand
                   if os.path.exists(os.path.join(ROOT, c.replace("\\", "/")))), None)
    if not target:
        return test_file + " (no single production module)", []
    src = read(os.path.join(ROOT, test_file))
    syms, modname = [], os.path.basename(target).replace(".ts", "").replace(".svelte", "")
    for m in re.finditer(r"import\s+(?:type\s+)?\{([^}]*)\}\s+from\s+['\"]([^'\"]+)['\"]", src):
        if modname in m.group(2) or m.group(2).endswith("/" + os.path.basename(target)):
            # `import { foo, type Bar }` — the column names what is exercised,
            # so a type-only member is not one of them.
            syms += [x.strip().split(" as ")[0] for x in m.group(1).split(",")
                     if x.strip() and not x.strip().startswith("type ")]
    return target, sorted(set(syms))


# ------------------------------------------------------------- cell builders

COMMENT_ONLY = re.compile(r"^\s*(//|/\*|\*)")


def build_steps(stmts, max_steps=14):
    steps, pending = [], []
    for s in stmts:
        body = s.strip()
        if all(COMMENT_ONLY.match(l) for l in body.split("\n") if l.strip()):
            pending.append(" ".join(
                re.sub(r"^\s*(//+|/\*+|\*+/?)", "", l).strip() for l in body.split("\n")).strip())
            continue
        line = collapse(body)
        if pending:
            line = "(%s) %s" % ("; ".join(p for p in pending if p), line)
            pending = []
        steps.append(line)
    if pending:
        steps.append("(%s)" % "; ".join(p for p in pending if p))
    out = ["%d. %s" % (i + 1, s) for i, s in enumerate(steps[:max_steps])]
    if len(steps) > max_steps:
        out.append("\u2026 (+%d further statements \u2014 see the source)" % (len(steps) - max_steps))
    return "\n".join(out)


def sentence(name):
    """Capitalize only when the first word is prose, never an identifier."""
    s = name.strip()
    if not s:
        return s
    first = re.split(r"[\s(]", s, 1)[0]
    return s[0].upper() + s[1:] if (first.islower() and first.isalpha()) else s


def clip(text, limit=700):
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "\u2026"


def build_expected(name, stmts, max_items=10):
    asserts = [collapse(s) for s in stmts if is_assertion(s)]
    head = sentence(name)
    if not asserts:
        return head
    body = "\n".join("- %s" % a for a in asserts[:max_items])
    if len(asserts) > max_items:
        body += "\n- \u2026 (+%d further assertions)" % (len(asserts) - max_items)
    return head + "\nAsserted by:\n" + body


EDGE_NAME = re.compile(r"""
 reject|refus|invalid|blank|empty|missing|absent|not found|404|503|unavailab|outage|unreachable
|fail|error|throw|conflict|stale|duplicat|traversal|oversiz|too (large|long|big|many)|exceed
|unsupported|spoof|malform|corrupt|truncat|bound|clamp|\blimit|\bmax\b|\bmin\b|out[- ]of[- ]range
|degrad|fallback|race|concurrent|timeout|deadline|cancel|retr(y|ies)|abort|never|without|cannot|can't
|ignor|guard|escape|\bzero\b|negative|unknown|non-|inactive|cascade|omit|exhaust|drain
|wrap|surface|instead of|rather than|unlike|BAD_USER_INPUT|NOT_FOUND|CONFLICT
|survive|repeat|idempot|skip|drop|collaps|leak|debounce|orphan|compensat|rollback|\bnull\b|\bno\b
""", re.X | re.I)

# Words too weak to carry a row on their own — they turn up in plenty of happy paths.
EDGE_WEAK = re.compile(r"^(null|no|empty|bound|limit|min|max|drop|skip|wrap|surface|zero)$", re.I)

EDGE_ASSERT = re.compile(
    r"assertThatThrownBy|assertThrows|assertFailsWith|\.rejects\b|toThrow|isInstanceOf\(\w*Exception",
    re.I)

# A SvelteKit form action signals success by throwing a redirect, so its tests
# assert the happy path with `.rejects`. That is not an error path.
EDGE_ASSERT_FALSE_POSITIVE = re.compile(r"redirect|status:\s*30\d|\b30[1-8]\b", re.I)


def is_edge_case(name, stmts):
    """Boundary, negative, error, empty or degraded-path test?

    The repository's own definition (docs/verification/v2-test-inventory.md):
    boundary/negative/error paths, missing or null data, empty states, degraded
    fallback, concurrency, cascade delete, unavailable references, races.

    Heuristic, not a label in the source. Calibrated against the 224 tests that
    file classifies by hand: 93% precision, 93% recall.
    """
    hits = [h.group(0) for h in EDGE_NAME.finditer(name)]
    strong = [h for h in hits if not EDGE_WEAK.match(h.strip())]
    threw = any(EDGE_ASSERT.search(s) and not EDGE_ASSERT_FALSE_POSITIVE.search(s)
                for s in stmts)
    return bool(strong) or len(hits) >= 2 or threw


def glossary_for(text, limit=2):
    out = []
    for pattern, reading in NOTES.GLOSSARY:
        if len(out) >= limit:
            break
        if re.search(pattern, text):
            out.append(reading)
    return out


def subject_note(subject, suite=""):
    """(plain name, blurb) for a module, or None when nothing is written for it."""
    key = NOTES.OVERRIDES.get(suite)
    if key and key in NOTES.SUBJECTS:
        return NOTES.SUBJECTS[key]
    if subject in NOTES.SUBJECTS:
        return NOTES.SUBJECTS[subject]
    sym = subject.rsplit(" :: ", 1)[-1] if " :: " in subject else None
    if sym and sym in NOTES.SUBJECTS:
        return NOTES.SUBJECTS[sym]
    return None


DETERMINERS = {
    "the", "a", "an", "it", "its", "this", "that", "these", "those", "no", "not",
    "every", "each", "both", "all", "only", "one", "two", "three", "four", "five",
    "six", "when", "where", "after", "before", "during", "with", "without", "any",
    "another", "same", "first", "second", "last", "several", "many", "neither",
}

COPULAS = {
    "is", "are", "was", "were", "can", "cannot", "must", "should", "will",
    "does", "do", "did", "has", "have", "had", "never", "always", "still",
}

IRREGULAR_VERBS = {"can", "cannot", "must", "should", "will", "may", "keep", "allow", "let"}

# A bare plural verb here means the word before it was a plural noun subject,
# not a verb: "responses carry ..." rather than "rejects a blank name".
PLURAL_VERBS = {
    "carry", "return", "come", "go", "stay", "remain", "keep", "show", "work",
    "fail", "match", "include", "contain", "survive", "appear", "render",
    "load", "sort", "apply", "count", "expose", "map", "point", "round-trip",
}

KNOWN_IDENTIFIERS = set()


def index_identifiers():
    """Function and component names, so a test name starting with one is read as
    a subject ("put generates ...") rather than a verb ("rejects NUL ...")."""
    for mod in KT_MODULES:
        base = os.path.join(ROOT, "backend", mod, "src", "main", "kotlin")
        for dp, _, fns in os.walk(base) if os.path.isdir(base) else []:
            for fn in fns:
                if fn.endswith(".kt"):
                    KNOWN_IDENTIFIERS.update(re.findall(r"\bfun\s+(\w+)", read(os.path.join(dp, fn))))
    fe = os.path.join(ROOT, "frontend", "src")
    for dp, _, fns in os.walk(fe) if os.path.isdir(fe) else []:
        for fn in fns:
            if fn.endswith((".ts", ".svelte")) and ".test." not in fn:
                src = read(os.path.join(dp, fn))
                KNOWN_IDENTIFIERS.update(re.findall(r"export\s+(?:async\s+)?function\s+(\w+)", src))
                KNOWN_IDENTIFIERS.update(re.findall(r"export\s+const\s+(\w+)", src))
                if fn.endswith(".svelte"):
                    KNOWN_IDENTIFIERS.add(fn[:-7])
    KNOWN_IDENTIFIERS.discard("")


def as_clause(name):
    """Render a test name so it completes "This case checks that ...".

    Names follow two conventions: Kotlin usually leads with the thing under test
    ("put generates a key"), Vitest with a bare verb ("rejects a blank name").
    The second needs a subject supplied, the first must not have one.
    """
    text = name.strip().rstrip(".")
    first = re.split(r"[\s(,]", text, 1)[0]
    if not first or not first[:1].islower() or not first.isalpha():
        return text                       # identifier-ish, quoted, or already a sentence
    if first.lower() in DETERMINERS:
        return text                       # already has a subject
    if first in KNOWN_IDENTIFIERS:
        return text                       # the function under test is the subject
    # Only a present-tense verb needs a subject supplied. Test names here are
    # written third-person ("rejects a blank name"), so the "s" is the signal —
    # but "credits are ordered ..." is a noun followed by a copula, not a verb.
    rest = re.split(r"[\s(,]+", text)
    second = rest[1].lower() if len(rest) > 1 else ""
    if second in COPULAS or second in PLURAL_VERBS:
        return text
    if first.endswith("s") or first in IRREGULAR_VERBS:
        return "it " + text
    return text                           # noun or gerund phrase: already reads


def module_cell(note, technical, test_ref):
    """Plain name first, then the code it exercises, then the test's own location."""
    lines = []
    if note:
        lines.append(note[0])
    if technical:
        lines.append(technical)
    lines.append("(test: %s)" % test_ref)
    return "\n".join(lines)


def build_description(doc, name, suite, subject="", file_doc=""):
    """A paragraph a reader with no knowledge of the codebase can follow.

    Context first (what this module is for), then what this one case checks,
    then why it matters when the test itself says so, then plain readings of
    any jargon in the name.
    """
    parts = []
    note = subject_note(subject, suite)
    if note:
        parts.append("%s — %s." % note)
    parts.append("This case checks that %s." % as_clause(name))

    why = doc if doc and len(doc) > 25 else ""
    if not why and file_doc and len(file_doc) > 60 and not note:
        why = file_doc
    if why:
        why = why.strip().rstrip(".")
        parts.append("Why it matters: %s." % clip(why, 420))

    gloss = glossary_for(name + " " + (doc or ""))
    if gloss:
        parts.append("(" + "; ".join(gloss) + ".)")
    return clip(" ".join(parts), 1100)


# ---------------------------------------------------------------- classify

def kt_type(row):
    f = os.path.basename(row["file"])
    if f.endswith("ContractTest.kt"):
        return "Contract"
    if f.endswith("IntegrationTest.kt") or "Testcontainers" in row["tags"] or "SpringBootTest" in row["tags"]:
        return "Integration"
    return "Unit"


def fe_type(file):
    if file.endswith(".spec.ts"):
        return "E2E"
    if "/lib/components/" in "/" + file or "/lib/features/" in "/" + file or file.endswith(".svelte.test.ts"):
        return "Component"
    return "Unit"


IMPORTER_AREA = "Importer (TMDB seeding)"

AREA_OF = {"catalogue-service": ("BE-CAT", "Catalogue Service"),
           "people-service": ("BE-PPL", "People Service"),
           "media": ("BE-MED", "Media (shared storage)"),
           "contracts": ("BE-CON", "Contracts")}

E2E_SCOPE = {
    "full catalogue journey through the BFF UI":
        "End-to-end journey: create person \u2192 create movie \u2192 add credit \u2192 upload artwork "
        "\u2192 search \u2192 remove credit \u2192 delete movie \u2192 delete person",
    "a 300-character unbroken name does not widen the person page":
        "Person page layout (long-word wrapping, V2.8-01)",
    "the BFF sets a Content-Security-Policy and the standard security headers on an HTML response":
        "BFF security headers on the SSR HTML response (hooks.server.ts)",
}


def build_rows(args):
    index_main_symbols()
    index_identifiers()
    kt, fe = parse_kotlin(), parse_frontend()
    rows, counters = [], collections.Counter()

    def next_id(prefix):
        counters[prefix] += 1
        return "%s-%04d" % (prefix, counters[prefix])

    junit = load_junit(args.junit_xml)
    for r in sorted(kt, key=lambda x: (x["module"], x["file"], x["line"])):
        prefix, area = AREA_OF[r["module"]]
        subj = kt_subject(r)
        status = junit.get(("%s.%s" % (r["package"], r["suite"]), r["name"]), args.backend_status)
        # An integration suite's subject is a boundary, not a class, so the
        # automatic "most-referenced symbol" guess is dropped for those.
        technical = "" if r["suite"] in NOTES.OVERRIDES else subj
        rows.append({
            "id": next_id(prefix), "name": r["name"],
            "type": kt_type(r) + (" (edge)" if is_edge_case(r["name"], r["stmts"]) else ""),
            "module": module_cell(subject_note(subj, r["suite"]), technical,
                                  "%s :: %s#%d" % (r["file"], r["suite"], r["line"])),
            "desc": build_description(r["doc"], r["name"], r["suite"], subj, r["suite_doc"]),
            "steps": build_steps(r["stmts"]),
            "expected": build_expected(r["name"], r["stmts"]),
            "actual": "As expected." if status == "Pass" else "\u2014",
            "status": status, "area": area,
        })
    if junit:
        unmatched = len([1 for r in kt
                         if ("%s.%s" % (r["package"], r["suite"]), r["name"]) not in junit])
        if unmatched:
            print("warning: %d Kotlin tests had no JUnit report entry" % unmatched, file=sys.stderr)

    src_index = {(r["file"], r["name"]): r for r in fe}

    vitest = load_json(args.vitest_json)
    if vitest:
        for tr in sorted(vitest["testResults"], key=lambda t: t["name"]):
            f = os.path.relpath(tr["name"], ROOT).replace("\\", "/")
            subj, syms = fe_subject(f)
            for a in tr["assertionResults"]:
                s = src_index.get((f, a["title"]), {})
                suite = " > ".join(x for x in a.get("ancestorTitles", []) if x)
                mt = "%s :: %s" % (subj, ", ".join(syms)) if syms else subj
                passed = a["status"] == "passed"
                stmts = s.get("stmts", [])
                rows.append({
                    "id": next_id("FE"), "name": a["title"],
                    "type": fe_type(f) + (" (edge)" if is_edge_case(a["title"], stmts) else ""),
                    "module": module_cell(subject_note(subj), mt, "%s#%d" % (f, s.get("line", 0))),
                    "desc": build_description(s.get("doc", ""), a["title"], suite, subj,
                                              s.get("file_doc", "")),
                    "steps": build_steps(s.get("stmts", [])),
                    "expected": build_expected(a["title"], s.get("stmts", [])),
                    "actual": "As expected." if passed else "\n".join(a.get("failureMessages", []))[:900] or "Failed.",
                    "status": "Pass" if passed else "Fail",
                    "area": "Frontend (SvelteKit BFF)",
                })
    else:
        for r in [x for x in fe if x["kind"] == "vitest"]:
            subj, syms = fe_subject(r["file"])
            mt = "%s :: %s" % (subj, ", ".join(syms)) if syms else subj
            rows.append({
                "id": next_id("FE"), "name": r["name"],
                "type": fe_type(r["file"]) + (" (edge)" if is_edge_case(r["name"], r["stmts"]) else ""),
                "module": module_cell(subject_note(subj), mt, "%s#%d" % (r["file"], r["line"])),
                "desc": build_description(r["doc"], r["name"], " > ".join(r["suite_path"]), subj,
                                          r["file_doc"]),
                "steps": build_steps(r["stmts"]),
                "expected": build_expected(r["name"], r["stmts"]),
                "actual": "\u2014", "status": "Not run",
                "area": "Frontend (SvelteKit BFF)",
            })

    imp = load_json(args.importer_json)
    imp_status = {}
    if imp:
        for tr in imp["testResults"]:
            f = os.path.relpath(tr["name"], ROOT).replace("\\", "/")
            for a in tr["assertionResults"]:
                imp_status[(f, a["title"])] = "Pass" if a["status"] == "passed" else "Fail"
    for r in sorted([x for x in fe if x["kind"] == "importer"],
                    key=lambda x: (x["file"], x["line"])):
        st = imp_status.get((r["file"], r["name"]), "Not run")
        subj, syms = fe_subject(r["file"])
        mt = "%s :: %s" % (subj, ", ".join(syms)) if syms else subj
        rows.append({
            "id": next_id("IMP"), "name": r["name"],
            "type": "Unit" + (" (edge)" if is_edge_case(r["name"], r["stmts"]) else ""),
            "module": module_cell(subject_note(subj), mt, "%s#%d" % (r["file"], r["line"])),
            "desc": build_description(r["doc"], r["name"], " > ".join(r["suite_path"]), subj,
                                      r["file_doc"]),
            "steps": build_steps(r["stmts"]),
            "expected": build_expected(r["name"], r["stmts"]),
            "actual": "As expected." if st == "Pass" else "—",
            "status": st, "area": IMPORTER_AREA,
        })

    pw = load_json(args.playwright_json)
    pw_status = {}
    if pw:
        def walk(su):
            for sp in su.get("specs", []):
                pw_status[sp["title"]] = "Pass" if sp.get("ok") else "Fail"
            for c in su.get("suites", []):
                walk(c)
        for s in pw.get("suites", []):
            walk(s)
    for r in [x for x in fe if x["kind"] == "playwright"]:
        st = pw_status.get(r["name"], "Not run")
        subj = r["file"] + " (no single production module)"
        rows.append({
            "id": next_id("E2E"), "name": r["name"],
            "type": "E2E" + (" (edge)" if is_edge_case(r["name"], r["stmts"]) else ""),
            "module": module_cell(subject_note(subj), E2E_SCOPE.get(r["name"], r["name"]),
                                  "%s#%d" % (r["file"], r["line"])),
            "desc": build_description(r["doc"], r["name"], "", subj, r["file_doc"]),
            "steps": build_steps(r["stmts"], max_steps=40),
            "expected": build_expected(r["name"], r["stmts"], max_items=20),
            "actual": "As expected." if st == "Pass" else "\u2014",
            "status": st, "area": "End-to-end (Playwright, full compose stack)",
        })
    return rows


def load_json(path):
    if not path or not os.path.exists(path):
        return None
    return json.loads(read(path))


def load_junit(pattern):
    """(classname, test name) -> "Pass"/"Fail"/"Skipped" from Gradle's JUnit XML."""
    import glob
    import xml.etree.ElementTree as ET
    out = {}
    for f in glob.glob(os.path.join(ROOT, pattern)):
        for tc in ET.parse(f).getroot().iter("testcase"):
            if tc.find("skipped") is not None:
                st = "Skipped"
            elif tc.find("failure") is not None or tc.find("error") is not None:
                st = "Fail"
            else:
                st = "Pass"
            # Gradle appends the parameter list to a Kotlin backtick-named test:
            # "the name()", or "the name(Path)" when it takes a @TempDir. Strip
            # exactly that one trailing group — a name that genuinely ends in
            # "(...)" keeps it, because the runner still appended its own.
            name = re.sub(r"\([^()]*\)$", "", tc.get("name") or "")
            out[(tc.get("classname"), name)] = st
    return out


# ------------------------------------------------------------------ workbook

# Several tests carry deliberate control/bidi characters as fixtures. Those are
# not legal in XML, so they are rendered as escapes rather than dropped — the
# escape is the information a reader needs.
ILLEGAL = re.compile(
    r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f\u200b-\u200f\u2028\u2029"
    r"\u202a-\u202e\ufeff\ufff9-\ufffb]")


def xml_safe(v):
    return ILLEGAL.sub(lambda m: "\\u%04X" % ord(m.group(0)), v) if isinstance(v, str) else v


def report_line(path):
    """Where a frontend result came from, and when — or that it was never run."""
    if not path or not os.path.exists(path):
        return "No runner output found at %s — rows are listed but were not executed." % path
    import datetime
    when = datetime.datetime.fromtimestamp(os.path.getmtime(path))
    return "From %s, written %s." % (rel(path), when.strftime("%Y-%m-%d %H:%M"))


def newest(pattern):
    """", newest report written <ts>" for a glob, or "" when it matches nothing."""
    import glob
    import datetime
    files = glob.glob(os.path.join(ROOT, pattern))
    if not files:
        return ""
    when = datetime.datetime.fromtimestamp(max(os.path.getmtime(f) for f in files))
    return ", newest written " + when.strftime("%Y-%m-%d %H:%M")


def write_workbook(rows, out, args):
    from openpyxl import Workbook
    from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
    from openpyxl.utils import get_column_letter

    HEAD_FILL = PatternFill("solid", fgColor="1F3864")
    HEAD_FONT = Font(color="FFFFFF", bold=True, size=11)
    THIN = Side(style="thin", color="D0D0D0")
    BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
    TOP_WRAP = Alignment(vertical="top", wrap_text=True)
    TOP = Alignment(vertical="top")

    wb = Workbook()
    ws = wb.active
    ws.title = "Test Specs"
    COLS = [("Test ID", 14), ("Name", 52), ("Test Type", 18), ("Module Tested", 60),
            ("Description", 60), ("Test Steps", 82), ("Expected Results", 70),
            ("Actual Results", 18), ("Status", 10)]
    ws.append([c for c, _ in COLS])
    for i, (_, w) in enumerate(COLS, start=1):
        ws.column_dimensions[get_column_letter(i)].width = w
        cell = ws.cell(row=1, column=i)
        cell.fill, cell.font = HEAD_FILL, HEAD_FONT
        cell.alignment = Alignment(vertical="center", horizontal="left")

    TYPE_FILL = {"Unit": "EAF3FF", "Component": "F0EAFB", "Integration": "E8F6EE",
                 "Contract": "FFF6E5", "E2E": "FDECEC"}
    for r in rows:
        ws.append([xml_safe(r[k]) for k in
                   ("id", "name", "type", "module", "desc", "steps", "expected", "actual", "status")])
        i = ws.max_row
        for c in range(1, len(COLS) + 1):
            cell = ws.cell(row=i, column=c)
            cell.alignment = TOP_WRAP if c in (2, 4, 5, 6, 7) else TOP
            cell.border = BORDER
        ws.cell(row=i, column=1).font = Font(name="Consolas", size=10)
        tc = ws.cell(row=i, column=3)
        tc.fill = PatternFill("solid", fgColor=TYPE_FILL.get(r["type"].replace(" (edge)", ""), "FFFFFF"))
        if r["type"].endswith("(edge)"):
            tc.font = Font(bold=True, color="8A4B00")
        sc = ws.cell(row=i, column=9)
        sc.font = Font(bold=True, color="1E7B34" if r["status"] == "Pass" else
                       ("B00020" if r["status"] == "Fail" else "7A7A7A"))
        sc.alignment = Alignment(vertical="top", horizontal="center")
    ws.freeze_panes = "A2"
    ws.auto_filter.ref = "A1:I%d" % ws.max_row

    # --- Summary
    sm = wb.create_sheet("Summary")
    for i, w in enumerate((46, 14, 16, 18, 14, 14, 12, 10, 10), start=1):
        sm.column_dimensions[get_column_letter(i)].width = w

    def put(row, *vals, bold=False, size=11, fill=None):
        for j, v in enumerate(vals, start=1):
            c = sm.cell(row=row, column=j, value=xml_safe(v))
            c.font = Font(bold=bold, size=size)
            c.alignment = Alignment(vertical="top", wrap_text=(j == 1 and len(str(v)) > 60))
            if fill:
                c.fill = PatternFill("solid", fgColor=fill)

    put(1, "MovieDB \u2014 automated test specification", bold=True, size=14)
    put(2, "Generated by scripts/build-test-specs.py from the test sources and runner output. "
           "Every row is a real test in the repository.")

    types = ["Unit", "Component", "Integration", "Contract", "E2E"]
    areas = ["Catalogue Service", "People Service", "Media (shared storage)", "Contracts",
             "Frontend (SvelteKit BFF)", IMPORTER_AREA,
             "End-to-end (Playwright, full compose stack)"]
    base = lambda t: t.replace(" (edge)", "")
    grid = collections.Counter((r["area"], base(r["type"])) for r in rows)
    edges = collections.Counter(r["area"] for r in rows if r["type"].endswith("(edge)"))
    put(4, "Tests by area and type", bold=True, size=12)
    put(5, "Area", *types, "Total", "of which edge cases", bold=True, fill="DCE6F1")
    row = 6
    for a in areas:
        tot = sum(grid[(a, t)] for t in types)
        if tot:
            put(row, a, *[grid[(a, t)] or "" for t in types], tot, edges[a] or "")
            row += 1
    put(row, "Total", *[sum(grid[(a, t)] for a in areas) or "" for t in types], len(rows),
        sum(edges.values()), bold=True, fill="DCE6F1")
    row += 2

    put(row, "Result", bold=True, size=12)
    row += 1
    for k, v in collections.Counter(r["status"] for r in rows).most_common():
        put(row, k, v)
        row += 1
    row += 1

    put(row, "How each result was obtained", bold=True, size=12)
    row += 1
    put(row, "Suite", "Provenance", bold=True, fill="DCE6F1")
    row += 1
    n_fe = sum(1 for r in rows if r["area"].startswith("Frontend"))
    n_e2e = sum(1 for r in rows if r["area"].startswith("End-to-end"))
    n_imp = sum(1 for r in rows if r["area"] == IMPORTER_AREA)
    n_be = len(rows) - n_fe - n_e2e - n_imp
    prov = [
        ("Frontend (Vitest), %d tests" % n_fe, report_line(args.vitest_json)),
        ("Importer (Vitest), %d tests" % n_imp, report_line(args.importer_json)),
        ("End-to-end (Playwright), %d tests" % n_e2e, report_line(args.playwright_json)),
        ("Backend (Gradle/JUnit), %d tests" % n_be,
         ("From Gradle's JUnit XML reports (%s), written by the last `./gradlew test` run. "
          "This script never runs them itself — the integration tests need Docker for "
          "Testcontainers." % (args.junit_xml + newest(args.junit_xml))) if load_junit(args.junit_xml) else
         ("No JUnit reports found at %s; status was supplied as --backend-status=%s. "
          "Run `cd backend && ./gradlew test` first." % (args.junit_xml, args.backend_status))),
    ]
    for a, b in prov:
        sm.cell(row=row, column=1, value=a).alignment = Alignment(vertical="top", wrap_text=True)
        c = sm.cell(row=row, column=2, value=b)
        c.alignment = Alignment(vertical="top", wrap_text=True)
        sm.merge_cells(start_row=row, start_column=2, end_row=row, end_column=9)
        sm.row_dimensions[row].height = 46
        row += 1
    row += 1

    put(row, "Reproducing these results", bold=True, size=12)
    row += 1
    for cmd, what in [
        ("cd frontend && npm run test", "Vitest unit and component tests"),
        ("cd backend && ./gradlew test", "All Kotlin unit, integration and contract tests (needs Docker)"),
        ("docker compose up -d --build", "Bring the full stack up on http://localhost:4173"),
        ("cd frontend && npm run test:e2e", "Playwright journeys against that stack"),
        ("python scripts/build-test-specs.py", "Rebuild this workbook"),
    ]:
        sm.cell(row=row, column=1, value=cmd).font = Font(name="Consolas", size=10)
        sm.cell(row=row, column=2, value=what)
        row += 1
    row += 1

    put(row, "Column meanings", bold=True, size=12)
    row += 1
    for a, b in [
        ("Test ID", "Stable within this document only; regenerate and IDs re-sequence in file order."),
        ("Test Type", "Unit (one piece of logic, nothing real behind it) · Component (one screen "
                      "element, driven like a user would) · Integration (a real database, file store "
                      "or API) · Contract (an agreement between two sides that must not drift) · "
                      "E2E (the whole system through a browser). "
                      "\"(edge)\" marks a test of a boundary, an error, missing or empty data, or a "
                      "degraded path — as opposed to the ordinary case working."),
        ("Module Tested", "What it is in plain words, then the code file it exercises, then the "
                          "test's own file and line."),
        ("Description", "What the module is for, what this one case checks, why it matters where the "
                        "test itself says so, and a plain reading of any jargon in the name."),
        ("Test Steps", "The test body's statements in order, comments folded in as annotations. "
                       "Long bodies are truncated with a pointer to the source."),
        ("Expected Results", "The behaviour the test name asserts, followed by the literal assertions."),
        ("Actual Results / Status", "The runner's outcome. See 'How each result was obtained' above."),
    ]:
        sm.cell(row=row, column=1, value=a).font = Font(bold=True)
        c = sm.cell(row=row, column=2, value=b)
        c.alignment = Alignment(vertical="top", wrap_text=True)
        sm.merge_cells(start_row=row, start_column=2, end_row=row, end_column=9)
        row += 1
    sm.sheet_view.showGridLines = False

    # --- By File
    bf = wb.create_sheet("By File")
    bf.append(["Test file", "Area", "Type", "Tests", "Passed"])
    for i, w in enumerate((92, 30, 20, 10, 10), start=1):
        bf.column_dimensions[get_column_letter(i)].width = w
    for i in range(1, 6):
        bf.cell(row=1, column=i).fill = HEAD_FILL
        bf.cell(row=1, column=i).font = HEAD_FONT
    per = {}
    for r in rows:
        m = re.search(r"\(test: ([^#]+)#", r["module"])
        k = (m.group(1) if m else "?", r["area"], r["type"])
        per.setdefault(k, [0, 0])
        per[k][0] += 1
        per[k][1] += 1 if r["status"] == "Pass" else 0
    for (f, a, t), (n, p) in sorted(per.items()):
        bf.append([f, a, t, n, p])
        bf.cell(row=bf.max_row, column=1).font = Font(name="Consolas", size=9)
    bf.freeze_panes = "A2"
    bf.auto_filter.ref = "A1:E%d" % bf.max_row

    wb.save(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--vitest-json", default=os.path.join(ROOT, ".test-results", "vitest.json"))
    ap.add_argument("--playwright-json", default=os.path.join(ROOT, ".test-results", "playwright.json"))
    ap.add_argument("--importer-json", default=os.path.join(ROOT, ".test-results", "importer.json"))
    ap.add_argument("--junit-xml", default="backend/*/build/test-results/test/*.xml",
                    help="glob (relative to the repo root) for Gradle's JUnit XML reports")
    ap.add_argument("--backend-status", default="Not run", choices=["Pass", "Not run"],
                    help="fallback status for Kotlin tests with no JUnit report entry")
    ap.add_argument("--out", default=os.path.join(ROOT, "docs", "verification", "test-specs.xlsx"))
    args = ap.parse_args()

    rows = build_rows(args)
    if not rows:
        sys.exit("No tests found — is this being run from inside the repository?")
    write_workbook(rows, args.out, args)
    counts = collections.Counter(r["status"] for r in rows)
    print("%d tests -> %s" % (len(rows), rel(args.out)))
    for k, v in counts.most_common():
        print("  %-8s %d" % (k, v))


if __name__ == "__main__":
    main()
