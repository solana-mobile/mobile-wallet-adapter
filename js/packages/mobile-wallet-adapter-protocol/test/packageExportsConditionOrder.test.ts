import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Conditional exports are matched in the order the package declares them, not in the order the
 * bundler lists its conditions. Metro asserts `browser` alongside `react-native` for native
 * builds, so if `browser` is declared first a React Native app silently receives the browser
 * bundle. For these packages that means `transact` keeps its `assertSecureContext()` call, which
 * throws `The mobile wallet adapter protocol must be used in a secure context (https)` on a
 * device. See #1179 and #1302, which are the same defect reported twice.
 */

const PACKAGES_DIR = path.resolve(__dirname, '..', '..');

type ExportsNode = string | null | ExportsNode[] | { [condition: string]: ExportsNode };

function readManifest(packageName: string): { exports?: ExportsNode } {
    return JSON.parse(readFileSync(path.join(PACKAGES_DIR, packageName, 'package.json'), 'utf8'));
}

function rootConditions(exportsField: ExportsNode | undefined): Record<string, ExportsNode> | undefined {
    if (exportsField == null || typeof exportsField !== 'object' || Array.isArray(exportsField)) {
        return undefined;
    }
    const hasSubpaths = Object.keys(exportsField).some((key) => key.startsWith('.'));
    const root = hasSubpaths ? exportsField['.'] : exportsField;
    return root != null && typeof root === 'object' && !Array.isArray(root)
        ? (root as Record<string, ExportsNode>)
        : undefined;
}

/** Node's condition matching: first declared key that is asserted, or `default`, wins. */
function resolveWithConditions(node: ExportsNode, conditions: Set<string>): string | undefined {
    if (typeof node === 'string') {
        return node;
    }
    if (node == null) {
        return undefined;
    }
    if (Array.isArray(node)) {
        for (const candidate of node) {
            const resolved = resolveWithConditions(candidate, conditions);
            if (resolved !== undefined) {
                return resolved;
            }
        }
        return undefined;
    }
    for (const condition of Object.keys(node)) {
        if (condition === 'default' || conditions.has(condition)) {
            const resolved = resolveWithConditions(node[condition], conditions);
            if (resolved !== undefined) {
                return resolved;
            }
        }
    }
    return undefined;
}

const packageNames = readdirSync(PACKAGES_DIR, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .filter((name) => {
        try {
            return rootConditions(readManifest(name).exports) !== undefined;
        } catch {
            return false;
        }
    });

describe.each(packageNames)('%s package.json exports', (packageName) => {
    const conditions = rootConditions(readManifest(packageName).exports)!;
    const declared = Object.keys(conditions);

    it.runIf(declared.includes('react-native') && declared.includes('browser'))(
        'declares `react-native` before `browser`',
        () => {
            expect(declared.indexOf('react-native')).toBeLessThan(declared.indexOf('browser'));
        },
    );

    it.runIf(declared.includes('types'))('declares `types` first', () => {
        expect(declared[0]).toBe('types');
    });

    it.runIf(declared.includes('react-native'))('resolves a React Native bundle to the native build', () => {
        // The condition set Metro asserts for a native bundle once package exports are enabled,
        // which React Native 0.79 and Expo SDK 53 turned on by default.
        const resolved = resolveWithConditions(conditions, new Set(['react-native', 'browser', 'require']));

        expect(resolved).toBeDefined();
        expect(resolved).toContain('native');
        expect(resolved).not.toContain('browser');
    });
});
