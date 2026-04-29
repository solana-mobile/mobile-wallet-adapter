import { describe, expect, it, vi } from 'vitest';

import createMobileWalletProxy from '../src/createMobileWalletProxy.js';
import { SolanaCloneAuthorization, SolanaSignTransactions } from '../src/types.js';

const APP_IDENTITY = {
    name: 'Test App',
} as const;
const AUTHORIZATION_RESULT = {
    accounts: [],
    auth_token: 'auth-token',
    wallet_uri_base: 'https://wallet.example',
} as const;

describe('createMobileWalletProxy', () => {
    it('does not behave like a promise and rejects proxy shape mutations', async () => {
        const wallet = createMobileWalletProxy('v1', vi.fn());

        await expect(Promise.resolve(wallet)).resolves.toBe(wallet);
        expect(Reflect.defineProperty(wallet, 'authorize', { value: vi.fn() })).toBe(false);
        expect(Reflect.deleteProperty(wallet, 'authorize')).toBe(false);
    });

    it('maps legacy authorize chains to clusters', async () => {
        const protocolRequestHandler = vi.fn().mockResolvedValue(AUTHORIZATION_RESULT);
        const wallet = createMobileWalletProxy('legacy', protocolRequestHandler);

        await expect(
            wallet.authorize({
                chain: 'solana:mainnet',
                identity: APP_IDENTITY,
            }),
        ).resolves.toBe(AUTHORIZATION_RESULT);

        expect(protocolRequestHandler).toHaveBeenCalledWith('authorize', {
            chain: 'solana:mainnet',
            cluster: 'mainnet-beta',
            identity: APP_IDENTITY,
        });
    });

    it('maps v1 authorize clusters to chains', async () => {
        const protocolRequestHandler = vi.fn().mockResolvedValue(AUTHORIZATION_RESULT);
        const wallet = createMobileWalletProxy('v1', protocolRequestHandler);

        await expect(
            wallet.authorize({
                chain: 'devnet',
                identity: APP_IDENTITY,
            }),
        ).resolves.toBe(AUTHORIZATION_RESULT);

        expect(protocolRequestHandler).toHaveBeenCalledWith('authorize', {
            chain: 'solana:devnet',
            identity: APP_IDENTITY,
        });
    });

    it('maps legacy authorize with an auth token to reauthorize', async () => {
        const protocolRequestHandler = vi.fn().mockResolvedValue(AUTHORIZATION_RESULT);
        const wallet = createMobileWalletProxy('legacy', protocolRequestHandler);

        await expect(
            wallet.authorize({
                auth_token: 'auth-token',
                identity: APP_IDENTITY,
            }),
        ).resolves.toBe(AUTHORIZATION_RESULT);

        expect(protocolRequestHandler).toHaveBeenCalledWith('reauthorize', {
            auth_token: 'auth-token',
            identity: APP_IDENTITY,
        });
    });

    it('maps v1 reauthorize to authorize', async () => {
        const protocolRequestHandler = vi.fn().mockResolvedValue(AUTHORIZATION_RESULT);
        const wallet = createMobileWalletProxy('v1', protocolRequestHandler);

        await expect(
            wallet.reauthorize({
                auth_token: 'auth-token',
                identity: APP_IDENTITY,
            }),
        ).resolves.toBe(AUTHORIZATION_RESULT);

        expect(protocolRequestHandler).toHaveBeenCalledWith('authorize', {
            auth_token: 'auth-token',
            identity: APP_IDENTITY,
        });
    });

    it('maps camel-case method names to snake-case RPC methods', async () => {
        const protocolRequestHandler = vi.fn().mockResolvedValue({ signatures: ['signature'] });
        const wallet = createMobileWalletProxy('v1', protocolRequestHandler);

        await expect(
            wallet.signAndSendTransactions({
                payloads: ['transaction'],
            }),
        ).resolves.toEqual({ signatures: ['signature'] });

        expect(protocolRequestHandler).toHaveBeenCalledWith('sign_and_send_transactions', {
            payloads: ['transaction'],
        });
    });

    it('adds feature identifiers to legacy capabilities responses', async () => {
        const protocolRequestHandler = vi.fn().mockResolvedValue({
            max_messages_per_request: 1,
            max_transactions_per_request: 2,
            supported_transaction_versions: ['legacy'],
            supports_clone_authorization: true,
            supports_sign_and_send_transactions: false,
        });
        const wallet = createMobileWalletProxy('legacy', protocolRequestHandler);

        await expect(wallet.getCapabilities()).resolves.toEqual({
            features: [SolanaSignTransactions, SolanaCloneAuthorization],
            max_messages_per_request: 1,
            max_transactions_per_request: 2,
            supported_transaction_versions: ['legacy'],
            supports_clone_authorization: true,
            supports_sign_and_send_transactions: false,
        });
    });

    it('adds legacy support booleans to v1 capabilities responses', async () => {
        const protocolRequestHandler = vi.fn().mockResolvedValue({
            features: [SolanaCloneAuthorization],
            max_messages_per_request: 1,
            max_transactions_per_request: 2,
            supported_transaction_versions: ['legacy'],
            supports_clone_authorization: false,
            supports_sign_and_send_transactions: false,
        });
        const wallet = createMobileWalletProxy('v1', protocolRequestHandler);

        await expect(wallet.getCapabilities()).resolves.toEqual({
            features: [SolanaCloneAuthorization],
            max_messages_per_request: 1,
            max_transactions_per_request: 2,
            supported_transaction_versions: ['legacy'],
            supports_clone_authorization: true,
            supports_sign_and_send_transactions: true,
        });
    });
});
