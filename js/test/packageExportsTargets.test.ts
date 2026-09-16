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

const packages = readdirSync(PACKAGES_ROOT)
    .map((dirname) => path.join(PACKAGES_ROOT, dirname))
    .filter((packageRoot) => existsSync(path.join(packageRoot, 'package.json')))
    .map((packageRoot) => {
        const manifest: Manifest = JSON.parse(readFileSync(path.join(packageRoot, 'package.json'), 'utf8'));
        return {
            hasBeenBuilt: existsSync(path.join(packageRoot, 'lib')),
            name: manifest.name,
            packageRoot,
            targets: collectManifestTargets(manifest),
        };
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
