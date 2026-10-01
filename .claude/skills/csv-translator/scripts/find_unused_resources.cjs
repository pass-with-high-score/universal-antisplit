#!/usr/bin/env node
/**
 * Find unused Android resources across multiple modules.
 *
 * Scans:
 *   - Value resources: strings, plurals, string-arrays, colors, dimens, styles
 *   - File resources: drawables, mipmaps, layouts, anims, raw, menus, fonts
 *
 * Usage:
 *   node find_unused_resources.cjs <module_dir> [<extra_search_dirs> ...] [--type strings|drawables|all]
 *
 * Example:
 *   node .agents/skills/csv-translator/scripts/find_unused_resources.cjs app core tv
 *   node .agents/skills/csv-translator/scripts/find_unused_resources.cjs app --type strings
 */

const fs = require('fs');
const path = require('path');

const CODE_EXT = new Set(['.kt', '.java', '.xml', '.kts', '.js', '.ts', '.pro', '.json', '.gradle']);

// Ignored files and names that are referenced dynamically or required by Android/tools
const IGNORED_NAMES = new Set([
    'app_name',
    'ic_launcher',
    'ic_launcher_round',
    'ic_launcher_background',
    'ic_launcher_foreground',
    'locales_config',
    'backup_rules',
    'data_extraction_rules',
    'network_security_config',
    'file_paths',
]);

function walk(dir, fileList = [], filterFn = null) {
    let entries;
    try {
        entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
        return fileList;
    }
    for (const e of entries) {
        const full = path.join(dir, e.name);
        if (e.isDirectory()) {
            if (e.name === 'build' || e.name === '.git' || e.name === 'node_modules' || e.name === '.gradle') continue;
            walk(full, fileList, filterFn);
        } else {
            if (!filterFn || filterFn(full, e.name)) {
                fileList.push(full);
            }
        }
    }
    return fileList;
}

function parseValueResources(xmlContent, tagName) {
    const names = [];
    const re = new RegExp(`<${tagName}(?:\\s[^>]*?)?\\sname="([^"]+)"`, 'g');
    let m;
    while ((m = re.exec(xmlContent)) !== null) {
        names.push(m[1]);
    }
    return names;
}

function collectDeclaredResources(resDir, typeFilter = 'all') {
    const resources = []; // { type, name, file, isFile }

    if (!fs.existsSync(resDir)) return resources;

    const subdirs = fs.readdirSync(resDir, { withFileTypes: true }).filter(d => d.isDirectory());

    for (const sub of subdirs) {
        const subName = sub.name;
        const subPath = path.join(resDir, subName);

        // 1. Value resources (values, values-*, etc.) - Only inspect base values/ to avoid duplicate key counts
        if (subName === 'values') {
            const xmlFiles = fs.readdirSync(subPath).filter(f => f.endsWith('.xml'));
            for (const xf of xmlFiles) {
                const filePath = path.join(subPath, xf);
                const content = fs.readFileSync(filePath, 'utf8');

                if (typeFilter === 'all' || typeFilter === 'strings' || typeFilter === 'string') {
                    for (const name of parseValueResources(content, 'string')) {
                        resources.push({ type: 'string', name, file: filePath, isFile: false });
                    }
                    for (const name of parseValueResources(content, 'plurals')) {
                        resources.push({ type: 'plurals', name, file: filePath, isFile: false });
                    }
                    for (const name of parseValueResources(content, 'string-array')) {
                        resources.push({ type: 'array', name, file: filePath, isFile: false });
                    }
                }
                if (typeFilter === 'all' || typeFilter === 'color' || typeFilter === 'colors') {
                    for (const name of parseValueResources(content, 'color')) {
                        resources.push({ type: 'color', name, file: filePath, isFile: false });
                    }
                }
                if (typeFilter === 'all' || typeFilter === 'dimen' || typeFilter === 'dimens') {
                    for (const name of parseValueResources(content, 'dimen')) {
                        resources.push({ type: 'dimen', name, file: filePath, isFile: false });
                    }
                }
            }
        }

        // 2. File-based resources (drawable, layout, anim, menu, raw, font, mipmap)
        const baseType = subName.split('-')[0];
        const fileTypes = ['drawable', 'layout', 'anim', 'animator', 'menu', 'raw', 'font', 'mipmap'];

        if (fileTypes.includes(baseType)) {
            if (typeFilter !== 'all' && typeFilter !== baseType && `${baseType}s` !== typeFilter) {
                continue;
            }
            const files = fs.readdirSync(subPath, { withFileTypes: true }).filter(f => f.isFile());
            for (const f of files) {
                const ext = path.extname(f.name);
                const name = path.basename(f.name, ext);
                if (name.startsWith('.')) continue;

                // Deduplicate density/qualifier variants by key
                const key = `${baseType}:${name}`;
                if (!resources.some(r => r.type === baseType && r.name === name)) {
                    resources.push({
                        type: baseType,
                        name,
                        file: path.join(subPath, f.name),
                        isFile: true,
                    });
                }
            }
        }
    }

    return resources;
}

function findUnusedResources(moduleDir, searchDirs, typeFilter = 'all') {
    const resDir = path.join(moduleDir, 'src', 'main', 'res');
    if (!fs.existsSync(resDir)) {
        console.error(`Error: Resource directory not found: ${resDir}`);
        process.exit(1);
    }

    const declared = collectDeclaredResources(resDir, typeFilter);
    if (declared.length === 0) {
        console.log(`No resources found matching filter '${typeFilter}' in ${resDir}`);
        return;
    }

    // Collect all source and config files across search dirs
    const allSearchFiles = searchDirs.flatMap(d =>
        walk(d, [], f => CODE_EXT.has(path.extname(f)))
    );

    // Read and combine haystack content
    console.log(`Scanning ${declared.length} resources against ${allSearchFiles.length} code/config files in [${searchDirs.join(', ')}]...`);

    const haystackMap = new Map();
    for (const f of allSearchFiles) {
        try {
            haystackMap.set(f, fs.readFileSync(f, 'utf8'));
        } catch {
            // Ignore unreadable
        }
    }

    const unused = [];

    for (const res of declared) {
        if (IGNORED_NAMES.has(res.name)) continue;

        // Common patterns for resource references:
        // - Kotlin/Java: R.string.my_name, R.drawable.my_name, etc.
        // - XML: @string/my_name, @drawable/my_name, ?attr/my_name, @+id/...
        // - Compose / Binding / Reflection: "my_name" in some cases
        const { type, name, file } = res;

        // Construct exact regex pattern
        // Matches: R.type.name, @type/name, @android:type/name
        const pattern = new RegExp(`(R\\.${type}\\.${name}|@(?:[a-zA-Z0-9_]+:)?${type}/${name})\\b`);

        let found = false;

        for (const [filePath, content] of haystackMap.entries()) {
            // Skip the file where the resource itself is declared if it's a file resource or the same values file
            if (res.isFile && path.resolve(filePath) === path.resolve(file)) continue;

            if (pattern.test(content)) {
                found = true;
                break;
            }
        }

        if (!found) {
            unused.push(res);
        }
    }

    return { total: declared.length, unused };
}

// ── CLI Execution ───────────────────────────────────────────────────────────

const rawArgs = process.argv.slice(2);
if (rawArgs.length === 0 || rawArgs.includes('--help') || rawArgs.includes('-h')) {
    console.log(`
Usage:
  node find_unused_resources.cjs <module_dir> [<extra_search_dirs> ...] [options]

Options:
  --type <type>    Filter by resource type: all (default), strings, drawables, colors, dimens, layouts, anims
  --json           Output result in JSON format

Examples:
  node find_unused_resources.cjs app
  node find_unused_resources.cjs app core tv --type strings
  node find_unused_resources.cjs app core tv --type drawables
`);
    process.exit(0);
}

let typeFilter = 'all';
const positionalArgs = [];
for (let i = 0; i < rawArgs.length; i++) {
    if (rawArgs[i] === '--type' && i + 1 < rawArgs.length) {
        typeFilter = rawArgs[++i].toLowerCase();
    } else if (!rawArgs[i].startsWith('--')) {
        positionalArgs.push(rawArgs[i]);
    }
}

const moduleDir = positionalArgs[0] || 'app';
const searchDirs = positionalArgs.length > 0 ? positionalArgs : ['app', 'core', 'tv'];

const result = findUnusedResources(moduleDir, searchDirs, typeFilter);
if (!result) process.exit(0);

const { total, unused } = result;

console.log('\n' + '='.repeat(60));
console.log(` RESOURCE USAGE REPORT for [${moduleDir}] (Filter: ${typeFilter})`);
console.log('='.repeat(60));

if (unused.length === 0) {
    console.log(`\n🎉 All ${total} resources in ${moduleDir} appear to be actively used.`);
} else {
    console.log(`\nFound ${unused.length} potentially UNUSED resources out of ${total} (${((unused.length / total) * 100).toFixed(1)}%):\n`);

    // Group by resource type
    const byType = {};
    for (const item of unused) {
        if (!byType[item.type]) byType[item.type] = [];
        byType[item.type].push(item);
    }

    for (const [type, items] of Object.entries(byType)) {
        console.log(`📦 ${type.toUpperCase()} (${items.length}):`);
        for (const item of items) {
            const relFile = path.relative(process.cwd(), item.file);
            console.log(`   • ${item.name.padEnd(35)} [${relFile}]`);
        }
        console.log('');
    }

    console.log('⚠️  Note: This is a static analysis heuristic. Check for runtime-generated names (e.g. getIdentifier) before deleting.');
}
console.log('='.repeat(60) + '\n');
