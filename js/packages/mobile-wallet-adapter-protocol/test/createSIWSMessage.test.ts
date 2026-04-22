// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

import { createSIWSMessage, createSIWSMessageBase64Url } from '../src/createSIWSMessage.js';

const PAYLOAD = {
    address: 'Address123',
    chainId: 'solana:mainnet',
    domain: 'example.com',
    expirationTime: '2024-01-03T03:04:05.000Z',
    issuedAt: '2024-01-02T03:04:05.000Z',
    nonce: 'nonce-123',
    notBefore: '2024-01-02T03:00:00.000Z',
    requestId: 'request-123',
    resources: ['https://example.com', 'ipfs://example'],
    statement: 'Sign in to continue.',
    uri: 'https://example.com/login',
    version: '1',
} as const;

const EXPECTED_MESSAGE = `example.com wants you to sign in with your Solana account:
Address123

Sign in to continue.

URI: https://example.com/login
Version: 1
Chain ID: solana:mainnet
Nonce: nonce-123
Issued At: 2024-01-02T03:04:05.000Z
Expiration Time: 2024-01-03T03:04:05.000Z
Not Before: 2024-01-02T03:00:00.000Z
Request ID: request-123
Resources:
- https://example.com
- ipfs://example`;

afterEach(() => {
    vi.restoreAllMocks();
});

describe('createSIWSMessage', () => {
    it('creates a sign-in message with the expected field order', () => {
        expect(createSIWSMessage(PAYLOAD)).toBe(EXPECTED_MESSAGE);
    });

    it('encodes the sign-in message as base64url', () => {
        const btoaSpy = vi.spyOn(window, 'btoa').mockReturnValue('abc+/==');

        expect(createSIWSMessageBase64Url(PAYLOAD)).toBe('abc-_');
        expect(btoaSpy).toHaveBeenCalledWith(EXPECTED_MESSAGE);
    });
});
