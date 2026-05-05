// @vitest-environment jsdom
import type { Transaction } from '@solana/transactions';
import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockBaseStartRemoteScenario, mockBaseTransact } = vi.hoisted(() => ({
    mockBaseStartRemoteScenario: vi.fn(),
    mockBaseTransact: vi.fn(),
}));

const { mockCompileTransaction, mockGetBase64EncodedWireTransaction, mockGetTransactionDecoder } = vi.hoisted(() => ({
    mockCompileTransaction: vi.fn(),
    mockGetBase64EncodedWireTransaction: vi.fn(),
    mockGetTransactionDecoder: vi.fn(),
}));

vi.mock('@solana-mobile/mobile-wallet-adapter-protocol', async () => {
    const actual = await vi.importActual<typeof import('@solana-mobile/mobile-wallet-adapter-protocol')>(
        '@solana-mobile/mobile-wallet-adapter-protocol',
    );

    return {
        ...actual,
        startRemoteScenario: mockBaseStartRemoteScenario,
        transact: mockBaseTransact,
    };
});

vi.mock('@solana/transactions', () => ({
    compileTransaction: mockCompileTransaction,
    getBase64EncodedWireTransaction: mockGetBase64EncodedWireTransaction,
    getTransactionDecoder: mockGetTransactionDecoder,
}));

import { type SignAndSendTransactionMessage, startRemoteScenario, transact } from '../src/transact.js';

afterEach(() => {
    mockBaseStartRemoteScenario.mockReset();
    mockBaseTransact.mockReset();
    mockCompileTransaction.mockReset();
    mockGetBase64EncodedWireTransaction.mockReset();
    mockGetTransactionDecoder.mockReset();
});

describe('transact', () => {
    it('wraps the base transact helper with an augmented wallet and preserves unknown wallet methods', async () => {
        const callback = vi.fn(async (wallet) => {
            const untypedWallet = wallet as typeof wallet & {
                experimentalMethod: typeof baseWallet.experimentalMethod;
            };

            expect(wallet).not.toBe(baseWallet);
            expect(wallet.signAndSendTransactions).not.toBe(baseWallet.signAndSendTransactions);
            expect(wallet.signMessages).not.toBe(baseWallet.signMessages);
            expect(wallet.signTransactions).not.toBe(baseWallet.signTransactions);
            expect(untypedWallet.experimentalMethod).toBe(baseWallet.experimentalMethod);

            return await untypedWallet.experimentalMethod('payload');
        });
        const config = { sentinel: 'transact' } as unknown as Parameters<typeof transact>[1];
        const baseWallet = createBaseWallet();

        mockBaseTransact.mockImplementation(async (augmentedCallback, receivedConfig) => {
            expect(augmentedCallback).not.toBe(callback);
            expect(receivedConfig).toBe(config);

            return await augmentedCallback(baseWallet);
        });

        await expect(transact(callback, config)).resolves.toBe('passthrough result');
        expect(callback).toHaveBeenCalledTimes(1);
    });

    it('converts kit transaction inputs and protocol signatures when signing and sending transactions', async () => {
        const compiledMessageTransaction = { messageBytes: new Uint8Array([4, 5, 6]) } as unknown as Transaction;
        const compiledTransaction = { messageBytes: new Uint8Array([1, 2, 3]) } as unknown as Transaction;
        const transactionMessage = { instructions: [] } as unknown as SignAndSendTransactionMessage;
        const baseWallet = createBaseWallet();

        mockCompileTransaction.mockReturnValue(compiledMessageTransaction);
        mockGetBase64EncodedWireTransaction
            .mockReturnValueOnce('compiled-payload')
            .mockReturnValueOnce('message-payload');
        baseWallet.signAndSendTransactions.mockResolvedValue({
            signatures: ['BAUG', 'BwgJ'],
        });
        mockBaseTransact.mockImplementation(async (augmentedCallback) => await augmentedCallback(baseWallet));

        await expect(
            transact((wallet) =>
                wallet.signAndSendTransactions({
                    commitment: 'confirmed',
                    maxRetries: 2,
                    minContextSlot: 123,
                    skipPreflight: true,
                    transactions: [compiledTransaction, transactionMessage],
                    waitForCommitmentToSendNextTransaction: false,
                }),
            ),
        ).resolves.toEqual([new Uint8Array([4, 5, 6]), new Uint8Array([7, 8, 9])]);

        expect(mockCompileTransaction).toHaveBeenCalledWith(transactionMessage);
        expect(mockGetBase64EncodedWireTransaction).toHaveBeenNthCalledWith(1, compiledTransaction);
        expect(mockGetBase64EncodedWireTransaction).toHaveBeenNthCalledWith(2, compiledMessageTransaction);
        expect(baseWallet.signAndSendTransactions).toHaveBeenCalledWith({
            options: {
                commitment: 'confirmed',
                max_retries: 2,
                min_context_slot: 123,
                skip_preflight: true,
                wait_for_commitment_to_send_next_transaction: false,
            },
            payloads: ['compiled-payload', 'message-payload'],
        });
    });

    it('omits signing options when signing and sending transactions without kit options', async () => {
        const compiledTransaction = { messageBytes: new Uint8Array([1, 2, 3]) } as unknown as Transaction;
        const baseWallet = createBaseWallet();

        mockGetBase64EncodedWireTransaction.mockReturnValue('compiled-payload');
        baseWallet.signAndSendTransactions.mockResolvedValue({
            signatures: ['BAUG'],
        });
        mockBaseTransact.mockImplementation(async (augmentedCallback) => await augmentedCallback(baseWallet));

        await transact((wallet) =>
            wallet.signAndSendTransactions({
                transactions: [compiledTransaction],
            }),
        );

        expect(baseWallet.signAndSendTransactions).toHaveBeenCalledWith({
            payloads: ['compiled-payload'],
        });
    });

    it('converts message payloads to protocol base64 and signed payloads back to bytes', async () => {
        const baseWallet = createBaseWallet();

        baseWallet.signMessages.mockResolvedValue({
            signed_payloads: ['BAUG', 'BwgJ'],
        });
        mockBaseTransact.mockImplementation(async (augmentedCallback) => await augmentedCallback(baseWallet));

        await expect(
            transact((wallet) =>
                wallet.signMessages({
                    addresses: ['address-1', 'address-2'],
                    payloads: [new Uint8Array([1, 2, 3]), new Uint8Array([10, 11, 12])],
                }),
            ),
        ).resolves.toEqual([new Uint8Array([4, 5, 6]), new Uint8Array([7, 8, 9])]);

        expect(baseWallet.signMessages).toHaveBeenCalledWith({
            addresses: ['address-1', 'address-2'],
            payloads: ['AQID', 'CgsM'],
        });
    });

    it('converts kit transactions to protocol payloads and signed payloads back to transactions', async () => {
        const decodedTransactionA = { decoded: 'a' };
        const decodedTransactionB = { decoded: 'b' };
        const compiledTransaction = { messageBytes: new Uint8Array([1, 2, 3]) } as unknown as Transaction;
        const baseWallet = createBaseWallet();
        const decoder = {
            decode: vi.fn().mockReturnValueOnce(decodedTransactionA).mockReturnValueOnce(decodedTransactionB),
        };

        mockGetBase64EncodedWireTransaction.mockReturnValue('compiled-payload');
        mockGetTransactionDecoder.mockReturnValue(decoder);
        baseWallet.signTransactions.mockResolvedValue({
            signed_payloads: ['BAUG', 'BwgJ'],
        });
        mockBaseTransact.mockImplementation(async (augmentedCallback) => await augmentedCallback(baseWallet));

        await expect(
            transact((wallet) =>
                wallet.signTransactions({
                    transactions: [compiledTransaction],
                }),
            ),
        ).resolves.toEqual([decodedTransactionA, decodedTransactionB]);

        expect(baseWallet.signTransactions).toHaveBeenCalledWith({
            payloads: ['compiled-payload'],
        });
        expect(decoder.decode).toHaveBeenNthCalledWith(1, new Uint8Array([4, 5, 6]));
        expect(decoder.decode).toHaveBeenNthCalledWith(2, new Uint8Array([7, 8, 9]));
    });

    it('rejects invalid transaction inputs before calling the base signing method', async () => {
        const baseWallet = createBaseWallet();

        mockBaseTransact.mockImplementation(async (augmentedCallback) => await augmentedCallback(baseWallet));

        await expect(
            transact((wallet) =>
                wallet.signTransactions({
                    transactions: [{} as unknown as Transaction],
                }),
            ),
        ).rejects.toThrow('Invalid transaction type');
        expect(baseWallet.signTransactions).not.toHaveBeenCalled();
    });

    it('prevents deleting and defining properties on the augmented wallet proxy', async () => {
        const baseWallet = createBaseWallet();

        mockBaseTransact.mockImplementation(async (augmentedCallback) => await augmentedCallback(baseWallet));

        await expect(
            transact((wallet) => {
                const proxy = wallet as typeof wallet & { transient?: unknown };

                expect(Reflect.defineProperty(proxy, 'transient', { value: true })).toBe(false);
                expect(Reflect.deleteProperty(proxy, 'signMessages')).toBe(false);

                return 'done';
            }),
        ).resolves.toBe('done');
    });

    it('wraps the base remote scenario with an augmented wallet promise and preserves associationUrl, close, and unknown wallet methods', async () => {
        const associationUrl = new URL('https://example.test/associate');
        const close = vi.fn();
        const config = { sentinel: 'remote' } as unknown as Parameters<typeof startRemoteScenario>[0];
        const baseWallet = createBaseWallet();

        mockBaseStartRemoteScenario.mockResolvedValue({
            associationUrl,
            close,
            wallet: Promise.resolve(baseWallet),
        });

        const scenario = await startRemoteScenario(config);
        const wallet = await scenario.wallet;
        const untypedWallet = wallet as typeof wallet & {
            experimentalMethod: typeof baseWallet.experimentalMethod;
        };

        expect(mockBaseStartRemoteScenario).toHaveBeenCalledWith(config);
        expect(scenario.associationUrl).toBe(associationUrl);
        expect(scenario.close).toBe(close);
        expect(wallet).not.toBe(baseWallet);
        expect(wallet.signAndSendTransactions).not.toBe(baseWallet.signAndSendTransactions);
        expect(wallet.signMessages).not.toBe(baseWallet.signMessages);
        expect(wallet.signTransactions).not.toBe(baseWallet.signTransactions);
        expect(untypedWallet.experimentalMethod).toBe(baseWallet.experimentalMethod);
    });
});

function createBaseWallet() {
    return {
        experimentalMethod: vi.fn().mockResolvedValue('passthrough result'),
        signAndSendTransactions: vi.fn(),
        signMessages: vi.fn(),
        signTransactions: vi.fn(),
    };
}
