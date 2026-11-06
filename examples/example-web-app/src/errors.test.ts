import { describe, expect, it } from 'vitest';

import { describeError, walletCall, WalletError } from './errors.js';

class ProtocolError extends Error {
    code = -3;
    name = 'SolanaMobileWalletAdapterProtocolError';
}

describe('describeError', () => {
    it('describes errors by name, message and code', () => {
        expect(describeError(new Error('boom'))).toBe('boom');
        expect(describeError(new TypeError('bad'))).toBe('TypeError: bad');
        expect(describeError(new ProtocolError('declined'))).toBe(
            'SolanaMobileWalletAdapterProtocolError: declined (-3)',
        );
    });

    it('follows the cause chain without repeating messages', () => {
        // How wallet-standard-mobile rethrows a declined request, wrapped by walletCall.
        const rethrown = new Error('declined', { cause: new ProtocolError('declined') });
        expect(describeError(new WalletError('WalletSignMessageError', '', rethrown))).toBe(
            'WalletSignMessageError: SolanaMobileWalletAdapterProtocolError: declined (-3)',
        );
        expect(describeError(new WalletError('WalletConnectionError', 'gave up', new Error('timeout')))).toBe(
            'WalletConnectionError: gave up: timeout',
        );
    });

    it('never prints [object Object] for non-Error values', () => {
        expect(describeError(undefined)).toBe('Unknown error');
        expect(describeError(null)).toBe('Unknown error');
        expect(describeError('')).toBe('Unknown error');
        expect(describeError('nope')).toBe('nope');
        expect(describeError(new Event('error'))).toBe('error event');
        expect(describeError({ message: 'rpc down', code: -32005 })).toBe('rpc down (-32005)');
        expect(describeError({ InstructionError: [0, 'Custom'] })).toBe('{"InstructionError":[0,"Custom"]}');
        expect(describeError({})).toBe('{}');
        expect(describeError(new WalletError('WalletSignInError', '', { foo: 1 }))).toBe(
            'WalletSignInError: {"foo":1}',
        );
        const circular: Record<string, unknown> = {};
        circular.self = circular;
        expect(describeError(circular)).toBe('Unreadable error with keys: self');
    });
});

describe('walletCall', () => {
    it('wraps failures in a named WalletError and passes results through', async () => {
        await expect(walletCall('WalletConnectionError', async () => 1)).resolves.toBe(1);
        const failure = walletCall('WalletConnectionError', () => Promise.reject(new Error('no')));
        await expect(failure).rejects.toMatchObject({ name: 'WalletConnectionError', cause: new Error('no') });
    });
});
