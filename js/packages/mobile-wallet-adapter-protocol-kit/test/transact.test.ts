import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockBaseStartRemoteScenario, mockBaseTransact } = vi.hoisted(() => ({
    mockBaseStartRemoteScenario: vi.fn(),
    mockBaseTransact: vi.fn(),
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

import { startRemoteScenario, transact } from '../src/transact.js';

afterEach(() => {
    mockBaseStartRemoteScenario.mockReset();
    mockBaseTransact.mockReset();
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
