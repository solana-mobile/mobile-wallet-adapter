import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockCreateScenario, nativeModules, platform } = vi.hoisted(() => ({
    mockCreateScenario: vi.fn(),
    nativeModules: {
        SolanaMobileWalletAdapterWalletLib: {
            createScenario: vi.fn(),
        },
    },
    platform: { OS: 'android' },
}));

nativeModules.SolanaMobileWalletAdapterWalletLib.createScenario = mockCreateScenario;

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
    platform.OS = 'android';
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
});
