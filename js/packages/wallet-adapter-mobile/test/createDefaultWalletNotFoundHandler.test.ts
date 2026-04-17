import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockDefaultErrorModalWalletNotFoundHandler } = vi.hoisted(() => ({
    mockDefaultErrorModalWalletNotFoundHandler: vi.fn(),
}));

vi.mock('@solana-mobile/wallet-standard-mobile', () => ({
    defaultErrorModalWalletNotFoundHandler: mockDefaultErrorModalWalletNotFoundHandler,
}));

import createDefaultWalletNotFoundHandler from '../src/createDefaultWalletNotFoundHandler.js';

afterEach(() => {
    mockDefaultErrorModalWalletNotFoundHandler.mockReset();
});

describe('createDefaultWalletNotFoundHandler', () => {
    it('returns a handler that forwards to the standard wallet-not-found handler', async () => {
        mockDefaultErrorModalWalletNotFoundHandler.mockResolvedValue(undefined);

        const handler = createDefaultWalletNotFoundHandler();
        const mobileWalletAdapter = {} as Parameters<typeof handler>[0];

        await expect(handler(mobileWalletAdapter)).resolves.toBeUndefined();
        expect(mockDefaultErrorModalWalletNotFoundHandler).toHaveBeenCalledTimes(1);
        expect(mockDefaultErrorModalWalletNotFoundHandler).toHaveBeenCalledWith();
    });
});
