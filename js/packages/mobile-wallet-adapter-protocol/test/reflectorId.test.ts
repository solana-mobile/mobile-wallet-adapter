// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

import { SolanaMobileWalletAdapterErrorCode } from '../src/errors.js';
import { assertReflectorId, getRandomReflectorId } from '../src/reflectorId.js';

const MAX_REFLECTOR_ID = 9007199254740991;

afterEach(() => {
    vi.restoreAllMocks();
});

describe('reflectorId', () => {
    it('accepts reflector ids at the supported boundaries', () => {
        expect(assertReflectorId(0)).toBe(0);
        expect(assertReflectorId(MAX_REFLECTOR_ID)).toBe(MAX_REFLECTOR_ID);
    });

    it('rejects reflector ids below the supported range', () => {
        expect(() => assertReflectorId(-1)).toThrowError(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_REFLECTOR_ID_OUT_OF_RANGE,
                data: { id: -1 },
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('rejects reflector ids above the supported range', () => {
        expect(() => assertReflectorId(MAX_REFLECTOR_ID + 1)).toThrowError(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_REFLECTOR_ID_OUT_OF_RANGE,
                data: { id: MAX_REFLECTOR_ID + 1 },
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('derives a reflector id from window.crypto', () => {
        const getRandomValuesSpy = vi.spyOn(window.crypto, 'getRandomValues').mockImplementation((buffer) => {
            const randomBuffer = buffer as Uint32Array;

            randomBuffer[0] = 0;
            return randomBuffer;
        });

        expect(getRandomReflectorId()).toBe(0);
        expect(getRandomValuesSpy).toHaveBeenCalledWith(expect.any(Uint32Array));
    });
});
