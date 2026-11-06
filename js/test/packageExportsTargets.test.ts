import { existsSync, readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Every package in this workspace ships ESM only. Guard against a manifest advertising output the
 * build does not produce, such as a `require` target under `lib/cjs` that no build step emits.
 *
 * CI builds before it tests, so the on-disk assertions run there; locally they are skipped for any
 * package until `pnpm build` has been run at least once.
 */

const PACKAGES_ROOT = path.resolve(__dirname, '..', 'packages');

type ExportsNode = string | null | ExportsNode[] | { [condition: string]: ExportsNode };

type Manifest = {
    browser?: Record<string, string>;
    exports?: ExportsNode;
    main?: string;
    module?: string;
    name: string;
    'react-native'?: string;
    types?: string;
};

/**
 * Walks an exports field and collects every relative target it can resolve to.
 *
 * Targets can sit at any depth (nested condition maps and fallback arrays), so the whole tree has to
 * be walked rather than just its top level. Only `./`-prefixed paths point at files a package ships.
 */
function collectExportsTargets(node: ExportsNode, found: string[] = []): string[] {
    if (typeof node === 'string') {
        if (node.startsWith('./')) {
            found.push(node);
        }
    } else if (Array.isArray(node)) {
        for (const child of node) {
            collectExportsTargets(child, found);
        }
    } else if (node != null && typeof node === 'object') {
        for (const child of Object.values(node)) {
            collectExportsTargets(child, found);
        }
    }
    return found;
}

/**
 * Collects every file a manifest points at: `exports` targets, the legacy top-level entry fields, and
 * both sides of the legacy `browser` field map.
 */
function collectManifestTargets(manifest: Manifest): string[] {
    const targets = collectExportsTargets(manifest.exports ?? null);
    for (const field of ['main', 'module', 'react-native', 'types'] as const) {
        const value = manifest[field];
        if (value != null) {
            targets.push(value);
        }
    }
    for (const [from, to] of Object.entries(manifest.browser ?? {})) {
        targets.push(from, to);
    }
    return Array.from(new Set(targets));
}

/**
 * Returns each subpath's condition map. An exports field is either one condition map (for `.`) or an
 * object keyed by subpaths (`.`, `./encoding`).
 */
function collectSubpathConditions(exports: ExportsNode | undefined): [string, Record<string, ExportsNode>][] {
    if (exports == null || typeof exports !== 'object' || Array.isArray(exports)) {
        return [];
    }
    const isSubpathMap = Object.keys(exports).some((key) => key.startsWith('.'));
    const subpaths = isSubpathMap ? Object.entries(exports) : [['.', exports] as const];
    return subpaths.filter(
        (entry): entry is [string, Record<string, ExportsNode>] =>
            entry[1] != null && typeof entry[1] === 'object' && !Array.isArray(entry[1]),
    );
}

/**
 * Resolves a condition map the way Node and Metro do: the first key, in the order the package
 * declares them, that is `default` or one of the active conditions wins. The order of the active
 * conditions themselves does not matter.
 */
function resolveConditions(node: ExportsNode, conditions: ReadonlySet<string>): string | null {
    if (typeof node === 'string' || node == null || Array.isArray(node)) {
        return typeof node === 'string' ? node : null;
    }
    for (const [condition, target] of Object.entries(node)) {
        if (condition === 'default' || conditions.has(condition)) {
            return resolveConditions(target, conditions);
        }
    }
    return null;
}

// Metro asserts `browser` alongside `react-native` when it bundles a React Native app.
const METRO_CONDITIONS = new Set(['import', 'react-native', 'browser']);
const BROWSER_CONDITIONS = new Set(['import', 'browser']);

const packages = readdirSync(PACKAGES_ROOT)
    .map((dirname) => path.join(PACKAGES_ROOT, dirname))
    .filter((packageRoot) => existsSync(path.join(packageRoot, 'package.json')))
    .map((packageRoot) => {
        const manifest: Manifest = JSON.parse(readFileSync(path.join(packageRoot, 'package.json'), 'utf8'));
        return {
            hasBeenBuilt: existsSync(path.join(packageRoot, 'lib')),
            name: manifest.name,
            packageRoot,
            subpathConditions: collectSubpathConditions(manifest.exports),
            targets: collectManifestTargets(manifest),
        };
    });

describe.each(packages)('$name export conditions', ({ subpathConditions }) => {
    it.each(subpathConditions.filter(([, conditions]) => 'react-native' in conditions))(
        '%s resolves to its react-native target in a React Native bundle',
        (_subpath, conditions) => {
            expect(resolveConditions(conditions, METRO_CONDITIONS)).toBe(conditions['react-native']);
        },
    );

    it.each(subpathConditions.filter(([, conditions]) => 'browser' in conditions))(
        '%s resolves to its browser target in a browser bundle',
        (_subpath, conditions) => {
            expect(resolveConditions(conditions, BROWSER_CONDITIONS)).toBe(conditions.browser);
        },
    );
});

describe.each(packages)('$name manifest targets', ({ hasBeenBuilt, packageRoot, targets }) => {
    it('declares at least one target', () => {
        expect(targets.length).toBeGreaterThan(0);
    });

    it('does not reference a CommonJS build', () => {
        expect(targets.filter((target) => target.includes('/cjs/'))).toEqual([]);
    });

    it('does not ship a CommonJS tsconfig', () => {
        expect(existsSync(path.join(packageRoot, 'tsconfig.cjs.json'))).toBe(false);
    });

    it.runIf(hasBeenBuilt).each(targets)('%s exists on disk', (target) => {
        expect(existsSync(path.join(packageRoot, target))).toBe(true);
    });
});
