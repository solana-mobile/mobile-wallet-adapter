import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * This package builds ESM only — see `tsdown.config.ts`, which emits `format: 'esm'` into
 * `lib/esm`. The manifest previously advertised a `require` target of `./lib/cjs/index.js`,
 * which no build step produces and which was therefore absent from the published tarball, so
 * `require('@solana-mobile/mobile-wallet-adapter-walletlib')` failed with MODULE_NOT_FOUND
 * pointing at a file that had never existed.
 *
 * Guard against a manifest advertising output the build does not produce. CI builds before it
 * tests, so the on-disk assertions run there; locally they are skipped until `pnpm build` has
 * been run at least once.
 */

const PACKAGE_ROOT = path.resolve(__dirname, '..');

type ExportsNode = string | null | ExportsNode[] | { [condition: string]: ExportsNode };

const manifest: { exports?: ExportsNode } = JSON.parse(readFileSync(path.join(PACKAGE_ROOT, 'package.json'), 'utf8'));

/**
 * Walks an exports field and collects every relative target it can resolve to.
 *
 * Targets can sit at any depth — nested condition maps, and fallback arrays — so the whole tree has
 * to be walked rather than just its top level. Bare specifiers such as `react-native` are skipped;
 * only `./`-prefixed paths point at files this package ships.
 *
 * @param node The `exports` field, or a node within it.
 * @param found Accumulator, for the recursive calls.
 * @returns Every relative target found, in declaration order and possibly with duplicates.
 */
function collectTargets(node: ExportsNode, found: string[] = []): string[] {
    if (typeof node === 'string') {
        if (node.startsWith('./')) {
            found.push(node);
        }
    } else if (Array.isArray(node)) {
        for (const child of node) {
            collectTargets(child, found);
        }
    } else if (node != null && typeof node === 'object') {
        for (const child of Object.values(node)) {
            collectTargets(child, found);
        }
    }
    return found;
}

const targets = Array.from(new Set(collectTargets(manifest.exports ?? null)));
const hasBeenBuilt = existsSync(path.join(PACKAGE_ROOT, 'lib'));

describe('package.json exports targets', () => {
    it('declares at least one target', () => {
        expect(targets.length).toBeGreaterThan(0);
    });

    it('does not reference a CommonJS build this package does not emit', () => {
        expect(targets.filter((target) => target.includes('/cjs/'))).toEqual([]);
    });

    it.runIf(hasBeenBuilt).each(targets)('%s exists on disk', (target) => {
        expect(existsSync(path.join(PACKAGE_ROOT, target))).toBe(true);
    });
});
