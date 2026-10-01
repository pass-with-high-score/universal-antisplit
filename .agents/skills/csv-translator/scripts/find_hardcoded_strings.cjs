#!/usr/bin/env node
/**
 * Find likely user-facing hardcoded strings in Android source files.
 *
 * Usage:
 *   node find_hardcoded_strings.cjs <project_or_module_dir> [<extra_dir> ...] [--all-literals]
 *
 * This is a heuristic audit. Review results before moving strings to
 * strings.xml because source code also contains package names, URIs, MIME
 * types, SQL, debug tags, file extensions, and other non-UI literals.
 */

const fs = require('fs');
const path = require('path');

const CODE_EXT = new Set(['.kt', '.java', '.xml']);
const SKIP_DIRS = new Set(['.git', '.gradle', '.idea', 'build', 'node_modules']);
const STRING_XML_RE = new RegExp(`${path.sep}res${path.sep}values[^${path.sep}]*${path.sep}strings\\.xml$`);
const XML_UI_ATTRS = new Set([
    'android:contentDescription',
    'android:hint',
    'android:label',
    'android:summary',
    'android:text',
    'android:title',
    'app:hint',
    'app:subtitle',
    'app:summary',
    'app:text',
    'app:title',
]);

function walk(dir, files = []) {
    let entries;
    try {
        entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
        return files;
    }

    for (const entry of entries) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            if (!SKIP_DIRS.has(entry.name)) walk(full, files);
        } else if (CODE_EXT.has(path.extname(entry.name)) && !STRING_XML_RE.test(full)) {
            files.push(full);
        }
    }
    return files;
}

function lineOf(content, index) {
    return content.slice(0, index).split('\n').length;
}

function previousCodeOnLine(content, index) {
    const lineStart = content.lastIndexOf('\n', index - 1) + 1;
    return content.slice(lineStart, index).trim();
}

function likelyUiStringContext(content, index) {
    const before = content.slice(Math.max(0, index - 180), index);
    return /(?:\bText\s*\(|\bButton\s*\(|\bTextButton\s*\(|\bAlertDialog\s*\(|\bListItem\s*\(|\bOutlinedTextField\s*\(|\bToast\.makeText\s*\(|\bsetText\s*\(|\bsetTitle\s*\(|\bsetMessage\s*\()/.test(before)
        || /(?:contentDescription|headlineContent|supportingContent|label|message|placeholder|summary|subtitle|text|title)\s*=\s*$/.test(before)
        || /(?:showToast|showSnackbar|snackbar|toast)\s*\([^)]*$/.test(before);
}

function unescapeJavaString(value) {
    return value
        .replace(/\\n/g, '\n')
        .replace(/\\t/g, '\t')
        .replace(/\\"/g, '"')
        .replace(/\\\\/g, '\\');
}

function looksTechnical(value) {
    const trimmed = value.trim();
    if (!trimmed) return true;
    if (trimmed.length === 1) return true;
    if (/^[{}[\]().,:;|/_\-+=*#%!?&<>]+$/.test(trimmed)) return true;
    if (/^[-+]?\d+(\.\d+)?$/.test(trimmed)) return true;
    if (/^%(\d+\$)?[sdfox]$/.test(trimmed)) return true;
    if (/^https?:\/\//i.test(trimmed)) return true;
    if (/^(content|file|package|market|mailto):/i.test(trimmed)) return true;
    if (/^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/.test(trimmed)) return true;
    if (/^[A-Z][A-Z0-9_]*(\.[A-Z0-9_]+)+$/.test(trimmed)) return true;
    if (/^[a-z]+\/[-+.a-z0-9]+$/i.test(trimmed)) return true;
    if (/^[a-z0-9_./-]+\.(apk|apks|apkm|xapk|json|xml|png|webp|txt|zip)$/i.test(trimmed)) return true;
    if (/^[a-z0-9_.-]+$/.test(trimmed) && !/[A-Z]/.test(trimmed)) return true;
    return false;
}

function looksUserFacing(value, includeAllLiterals) {
    if (includeAllLiterals) return value.trim().length > 0;
    if (looksTechnical(value)) return false;
    return /\s/.test(value) || /[A-Z]/.test(value) || /[^\x00-\x7F]/.test(value);
}

function collectCodeStrings(file, content, includeAllLiterals) {
    const results = [];
    const tripleRe = /"""([\s\S]*?)"""/g;
    const quotedRe = /"((?:\\.|[^"\\])*)"/g;

    for (const re of [tripleRe, quotedRe]) {
        let match;
        while ((match = re.exec(content)) !== null) {
            const raw = re === quotedRe ? unescapeJavaString(match[1]) : match[1];
            if (!looksUserFacing(raw, includeAllLiterals)) continue;
            if (!includeAllLiterals && !likelyUiStringContext(content, match.index)) continue;

            const previous = previousCodeOnLine(content, match.index);
            if (/^(package|import)\s/.test(previous)) continue;
            if (/(Log|Timber)\.[a-z]+\s*\($/.test(previous)) continue;

            results.push({
                file,
                line: lineOf(content, match.index),
                value: raw.replace(/\s+/g, ' ').trim(),
            });
        }
    }

    return results;
}

function collectXmlStrings(file, content, includeAllLiterals) {
    const results = [];
    const attrRe = /([\w:.-]+)\s*=\s*"([^"]*)"/g;
    let match;
    while ((match = attrRe.exec(content)) !== null) {
        const [, attr, value] = match;
        if (!XML_UI_ATTRS.has(attr)) continue;
        if (value.startsWith('@') || value.startsWith('?')) continue;
        if (!looksUserFacing(value, includeAllLiterals)) continue;
        results.push({
            file,
            line: lineOf(content, match.index),
            value: value.replace(/\s+/g, ' ').trim(),
        });
    }
    return results;
}

const args = process.argv.slice(2);
const includeAllLiterals = args.includes('--all-literals');
const roots = args.filter(arg => arg !== '--all-literals');

if (roots.length === 0) {
    console.log('Usage: node find_hardcoded_strings.cjs <project_or_module_dir> [<extra_dir> ...] [--all-literals]');
    process.exit(1);
}

const findings = [];
for (const root of roots) {
    for (const file of walk(root)) {
        let content;
        try {
            content = fs.readFileSync(file, 'utf8');
        } catch {
            continue;
        }

        const ext = path.extname(file);
        const fileFindings = ext === '.xml'
            ? collectXmlStrings(file, content, includeAllLiterals)
            : collectCodeStrings(file, content, includeAllLiterals);
        findings.push(...fileFindings);
    }
}

if (findings.length === 0) {
    console.log('No likely hardcoded user-facing strings found.');
} else {
    console.log(`${findings.length} likely hardcoded user-facing strings found:\n`);
    findings
        .sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line)
        .forEach(item => console.log(`${item.file}:${item.line}: ${item.value}`));
    console.log('\nHeuristic only — move true UI text into strings.xml and leave technical literals in code.');
}
