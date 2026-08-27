import { describe, expect, it, vi } from 'vitest';

import { getAsyncStorage } from '../src/__forks__/react-native/getAsyncStorage.js';

const CALLER = 'createDefaultAuthorizationCache';
const EXPECTED_ERROR = /`createDefaultAuthorizationCache\(\)` requires `@react-native-async-storage\/async-storage`/;

function createAsyncStorage() {
    return {
        getItem: vi.fn(async () => null),
        removeItem: vi.fn(async () => {}),
        setItem: vi.fn(async () => {}),
    };
}

describe('react-native getAsyncStorage fork', () => {
    it('accepts a module whose default export is usable', () => {
        const asyncStorage = createAsyncStorage();

        expect(getAsyncStorage(CALLER, () => ({ default: asyncStorage }))).toBe(asyncStorage);
    });

    it('accepts a module that is itself usable', () => {
        const asyncStorage = createAsyncStorage();

        expect(getAsyncStorage(CALLER, () => asyncStorage)).toBe(asyncStorage);
    });

    // The loader returns `undefined` when the optional peer cannot be resolved; Metro's optional
    // dependency handling defers that failure from build time to this call.
    it('throws an error naming the caller when the module cannot be resolved', () => {
        expect(() => getAsyncStorage(CALLER, () => undefined)).toThrow(EXPECTED_ERROR);
    });

    it('throws an error naming the caller when the module resolves but is unusable', () => {
        expect(() => getAsyncStorage(CALLER, () => ({ default: {} }))).toThrow(EXPECTED_ERROR);
    });

    // The cache calls all three methods, so a partially usable export must be rejected too.
    it('rejects a default export that is missing removeItem and setItem', () => {
        expect(() => getAsyncStorage(CALLER, () => ({ default: { getItem: async () => null } }))).toThrow(
            EXPECTED_ERROR,
        );
    });
});
