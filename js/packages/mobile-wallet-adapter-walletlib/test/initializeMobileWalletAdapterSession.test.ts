import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockCreateScenario, nativeModules, nativeWalletLib, platform } = vi.hoisted(() => {
    const mockCreateScenario = vi.fn();
    const nativeWalletLib = {
        createScenario: vi.fn(),
    };
    nativeWalletLib.createScenario = mockCreateScenario;

    return {
        mockCreateScenario,
        nativeModules: {
            SolanaMobileWalletAdapterWalletLib: nativeWalletLib as
                | { createScenario: ReturnType<typeof vi.fn> }
                | undefined,
        },
        nativeWalletLib,
        platform: { OS: 'android' },
    };
});

vi.mock('react-native', () => ({
    NativeModules: nativeModules,
    Platform: platform,
}));

import { SolanaMWAWalletLibError, SolanaMWAWalletLibErrorCode } from '../src/errors.js';
import {
    initializeMobileWalletAdapterSession,
    type MobileWalletAdapterConfig,
} from '../src/initializeMobileWalletAdapterSession.js';

const CONFIG: MobileWalletAdapterConfig = {
    maxMessagesPerSigningRequest: 2,
    maxTransactionsPerSigningRequest: 3,
    noConnectionWarningTimeoutMs: 5000,
    optionalFeatures: [],
    supportedTransactionVersions: ['legacy'],
};

afterEach(() => {
    mockCreateScenario.mockReset();
    nativeModules.SolanaMobileWalletAdapterWalletLib = nativeWalletLib;
    platform.OS = 'android';
    vi.resetModules();
});

describe('initializeMobileWalletAdapterSession', () => {
    it('creates a scenario with the serialized config', async () => {
        mockCreateScenario.mockResolvedValue('session-1');

        await expect(initializeMobileWalletAdapterSession('Example Wallet', CONFIG)).resolves.toBe('session-1');
        expect(mockCreateScenario).toHaveBeenCalledWith('Example Wallet', JSON.stringify(CONFIG));
    });

    it('wraps native walletlib errors with the package error class', async () => {
        mockCreateScenario.mockRejectedValue(
            Object.assign(new Error('Session already created'), {
                code: SolanaMWAWalletLibErrorCode.ERROR_SESSION_ALREADY_CREATED,
            }),
        );

        await expect(initializeMobileWalletAdapterSession('Example Wallet', CONFIG)).rejects.toBeInstanceOf(
            SolanaMWAWalletLibError,
        );
        await expect(initializeMobileWalletAdapterSession('Example Wallet', CONFIG)).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMWAWalletLibErrorCode.ERROR_SESSION_ALREADY_CREATED,
                message: 'Session already created',
                name: 'SolanaMWAWalletLibError',
            }),
        );
    });

    it('rethrows unknown native errors unchanged', async () => {
        const error = new Error('Unexpected failure');

        mockCreateScenario.mockRejectedValue(error);

        await expect(initializeMobileWalletAdapterSession('Example Wallet', CONFIG)).rejects.toBe(error);
    });

    it('rethrows unknown native non-error rejections unchanged', async () => {
        mockCreateScenario.mockRejectedValue('Unexpected failure');

        await expect(initializeMobileWalletAdapterSession('Example Wallet', CONFIG)).rejects.toBe('Unexpected failure');
    });

    it('throws a linking error when the Android native module is missing', async () => {
        nativeModules.SolanaMobileWalletAdapterWalletLib = undefined;
        vi.resetModules();
        const { initializeMobileWalletAdapterSession } = await import('../src/initializeMobileWalletAdapterSession.js');

        await expect(initializeMobileWalletAdapterSession('Example Wallet', CONFIG)).rejects.toThrow(
            "The package 'solana-mobile-wallet-adapter-walletlib' doesn't seem to be linked.",
        );
    });

    it('throws a platform error outside Android', async () => {
        platform.OS = 'ios';
        vi.resetModules();
        const { initializeMobileWalletAdapterSession } = await import('../src/initializeMobileWalletAdapterSession.js');

        await expect(initializeMobileWalletAdapterSession('Example Wallet', CONFIG)).rejects.toThrow(
            'is only compatible with React Native Android',
        );
    });
});
