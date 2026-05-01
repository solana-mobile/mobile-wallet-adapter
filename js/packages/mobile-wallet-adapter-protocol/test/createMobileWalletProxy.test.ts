// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';

import { fromUint8Array } from '../src/base64Utils.js';
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

    it.each([
        ['solana:devnet', 'devnet'],
        ['solana:testnet', 'testnet'],
    ] as const)('maps legacy authorize chain %s to cluster %s', async (chain, cluster) => {
        const protocolRequestHandler = vi.fn().mockResolvedValue(AUTHORIZATION_RESULT);
        const wallet = createMobileWalletProxy('legacy', protocolRequestHandler);

        await wallet.authorize({
            chain,
            identity: APP_IDENTITY,
        });

        expect(protocolRequestHandler).toHaveBeenCalledWith('authorize', {
            chain,
            cluster,
            identity: APP_IDENTITY,
        });
    });

    it('falls back to the legacy cluster when a legacy authorize chain is not supplied', async () => {
        const protocolRequestHandler = vi.fn().mockResolvedValue(AUTHORIZATION_RESULT);
        const wallet = createMobileWalletProxy('legacy', protocolRequestHandler);

        await wallet.authorize({
            cluster: 'devnet',
            identity: APP_IDENTITY,
        });

        expect(protocolRequestHandler).toHaveBeenCalledWith('authorize', {
            cluster: 'devnet',
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

    it.each([
        ['mainnet-beta', 'solana:mainnet'],
        ['testnet', 'solana:testnet'],
    ] as const)('maps v1 authorize cluster %s to chain %s', async (cluster, chain) => {
        const protocolRequestHandler = vi.fn().mockResolvedValue(AUTHORIZATION_RESULT);
        const wallet = createMobileWalletProxy('v1', protocolRequestHandler);

        await wallet.authorize({
            chain: cluster,
            identity: APP_IDENTITY,
        });

        expect(protocolRequestHandler).toHaveBeenCalledWith('authorize', {
            chain,
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

    it('omits clone authorization from legacy capabilities responses when unsupported', async () => {
        const protocolRequestHandler = vi.fn().mockResolvedValue({
            max_messages_per_request: 1,
            max_transactions_per_request: 2,
            supported_transaction_versions: ['legacy'],
            supports_clone_authorization: false,
            supports_sign_and_send_transactions: false,
        });
        const wallet = createMobileWalletProxy('legacy', protocolRequestHandler);

        await expect(wallet.getCapabilities()).resolves.toEqual({
            features: [SolanaSignTransactions],
            max_messages_per_request: 1,
            max_transactions_per_request: 2,
            supported_transaction_versions: ['legacy'],
            supports_clone_authorization: false,
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

    it('falls back to signing a SIWS message when authorize omits the sign-in result', async () => {
        const address = fromUint8Array(Uint8Array.of(1, 2, 3, 4));
        const signature = Uint8Array.from({ length: 64 }, (_, index) => index);
        const signatureBase64 = fromUint8Array(signature);
        const protocolRequestHandler = vi
            .fn()
            .mockResolvedValueOnce({
                accounts: [{ address }],
                auth_token: 'auth-token',
                wallet_uri_base: 'https://wallet.example',
            })
            .mockResolvedValueOnce({
                signed_payloads: [signatureBase64],
            });
        const wallet = createMobileWalletProxy('v1', protocolRequestHandler);

        const result = await wallet.authorize({
            chain: 'solana:mainnet',
            identity: APP_IDENTITY,
            sign_in_payload: {
                domain: 'example.test',
                nonce: 'nonce-123',
            },
        });

        expect(result.sign_in_result).toEqual({
            address,
            signature: signatureBase64,
            signed_message: expect.any(String),
        });
        expect(protocolRequestHandler).toHaveBeenNthCalledWith(2, 'sign_messages', {
            addresses: [address],
            payloads: [result.sign_in_result?.signed_message],
        });
    });

    it('uses the signed SIWS message returned by the wallet before the signature', async () => {
        const address = fromUint8Array(Uint8Array.of(1, 2, 3, 4));
        const signature = Uint8Array.from({ length: 64 }, (_, index) => index);
        const signedMessage = Uint8Array.of(9, 8, 7);
        const protocolRequestHandler = vi
            .fn()
            .mockResolvedValueOnce({
                accounts: [{ address }],
                auth_token: 'auth-token',
                wallet_uri_base: 'https://wallet.example',
            })
            .mockResolvedValueOnce({
                signed_payloads: [fromUint8Array(Uint8Array.of(...signedMessage, ...signature))],
            });
        const wallet = createMobileWalletProxy('v1', protocolRequestHandler);

        const result = await wallet.authorize({
            chain: 'solana:mainnet',
            identity: APP_IDENTITY,
            sign_in_payload: {
                domain: 'example.test',
                nonce: 'nonce-123',
            },
        });

        expect(result.sign_in_result).toEqual({
            address,
            signature: fromUint8Array(signature),
            signed_message: fromUint8Array(signedMessage),
        });
    });
});
