import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockInitializeMWAEventListener, mockInitializeMobileWalletAdapterSession, mockRemove, mockUseEffect } =
    vi.hoisted(() => ({
        mockInitializeMWAEventListener: vi.fn(),
        mockInitializeMobileWalletAdapterSession: vi.fn(),
        mockRemove: vi.fn(),
        mockUseEffect: vi.fn(),
    }));

vi.mock('react', () => ({
    useEffect: mockUseEffect,
}));

vi.mock('../src/initializeMWAEventListener.js', () => ({
    initializeMWAEventListener: mockInitializeMWAEventListener,
}));

vi.mock('../src/initializeMobileWalletAdapterSession.js', () => ({
    initializeMobileWalletAdapterSession: mockInitializeMobileWalletAdapterSession,
}));

import { type MobileWalletAdapterConfig } from '../src/initializeMobileWalletAdapterSession.js';
import { useMobileWalletAdapterSession } from '../src/useMobileWalletAdapterSession.js';

const CONFIG: MobileWalletAdapterConfig = {
    maxMessagesPerSigningRequest: 2,
    maxTransactionsPerSigningRequest: 3,
    noConnectionWarningTimeoutMs: 5000,
    optionalFeatures: [],
    supportedTransactionVersions: ['legacy'],
};

afterEach(() => {
    mockInitializeMWAEventListener.mockReset();
    mockInitializeMobileWalletAdapterSession.mockReset();
    mockRemove.mockReset();
    mockUseEffect.mockReset();
    vi.restoreAllMocks();
});

describe('useMobileWalletAdapterSession', () => {
    it('initializes the session and removes the listener on cleanup', () => {
        const handleRequest = vi.fn();
        const handleSessionEvent = vi.fn();
        let cleanup: (() => void) | undefined;

        mockInitializeMWAEventListener.mockReturnValue({ remove: mockRemove });
        mockInitializeMobileWalletAdapterSession.mockResolvedValue('session-1');
        mockUseEffect.mockImplementation((effect: () => void | (() => void)) => {
            cleanup = effect() as (() => void) | undefined;
        });

        useMobileWalletAdapterSession('Example Wallet', CONFIG, handleRequest, handleSessionEvent);

        expect(mockInitializeMWAEventListener).toHaveBeenCalledWith(handleRequest, handleSessionEvent);
        expect(mockInitializeMobileWalletAdapterSession).toHaveBeenCalledWith('Example Wallet', CONFIG);
        expect(cleanup).toBeTypeOf('function');

        cleanup?.();

        expect(mockRemove).toHaveBeenCalledWith();
    });

    it('logs session initialization errors', async () => {
        const error = new Error('Session failed');
        const handleRequest = vi.fn();
        const handleSessionEvent = vi.fn();
        const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

        mockInitializeMWAEventListener.mockReturnValue({ remove: mockRemove });
        mockInitializeMobileWalletAdapterSession.mockRejectedValue(error);
        mockUseEffect.mockImplementation((effect: () => void | (() => void)) => {
            effect();
        });

        useMobileWalletAdapterSession('Example Wallet', CONFIG, handleRequest, handleSessionEvent);
        await Promise.resolve();

        expect(errorSpy).toHaveBeenCalledWith(error);
    });
});
