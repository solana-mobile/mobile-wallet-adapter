import { afterEach, describe, expect, it, vi } from 'vitest';

import { assertAssociationPort, getRandomAssociationPort } from '../src/associationPort.js';
import { SolanaMobileWalletAdapterErrorCode } from '../src/errors.js';

afterEach(() => {
    vi.restoreAllMocks();
});

describe('associationPort', () => {
    it('accepts association ports at the supported boundaries', () => {
        expect(assertAssociationPort(49152)).toBe(49152);
        expect(assertAssociationPort(65535)).toBe(65535);
    });

    it('rejects association ports below the supported range', () => {
        expect(() => assertAssociationPort(49151)).toThrowError(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_ASSOCIATION_PORT_OUT_OF_RANGE,
                data: { port: 49151 },
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('rejects association ports above the supported range', () => {
        expect(() => assertAssociationPort(65536)).toThrowError(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_ASSOCIATION_PORT_OUT_OF_RANGE,
                data: { port: 65536 },
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('derives the lowest valid association port from Math.random', () => {
        vi.spyOn(Math, 'random').mockReturnValue(0);

        expect(getRandomAssociationPort()).toBe(49152);
    });

    it('derives the highest valid association port from Math.random', () => {
        vi.spyOn(Math, 'random').mockReturnValue(0.99999);

        expect(getRandomAssociationPort()).toBe(65535);
    });
});
