import { afterEach, describe, expect, it, vi } from 'vitest';

import { SolanaMobileWalletAdapterErrorCode } from '../src/errors.js';
import type { WalletAssociationConfig } from '../src/types.js';

const WALLET = {} as const;

const { mockCreateMobileWalletProxy } = vi.hoisted(() => ({
    mockCreateMobileWalletProxy: vi.fn(),
}));

afterEach(() => {
    mockCreateMobileWalletProxy.mockReset();
    vi.doUnmock('react-native');
    vi.doUnmock('../src/codegenSpec/NativeSolanaMobileWalletAdapter.js');
    vi.doUnmock('../src/createMobileWalletProxy.js');
    vi.resetModules();
    vi.restoreAllMocks();
});

describe('react-native transact fork', () => {
    it('starts a native session, proxies wallet calls, and ends the session', async () => {
        const nativeModule = createNativeModule();
        mockCreateMobileWalletProxy.mockImplementation((_protocolVersion, requestHandler) => ({
            signMessages: (params: unknown) => requestHandler('sign_messages', params),
        }));
        nativeModule.invoke.mockReturnValue('invoke-result');

        const { appRegistry, transact } = await importReactNativeTransact({ nativeModule });
        const config: WalletAssociationConfig = { baseUri: 'https://wallet.example' };

        const headlessTaskFactory = appRegistry.registerHeadlessTask.mock.calls[0][1];
        await expect(headlessTaskFactory()()).resolves.toBeUndefined();
        await expect(
            transact(
                (wallet) =>
                    wallet.signMessages({
                        addresses: [],
                        payloads: [],
                    }),
                config,
            ),
        ).resolves.toBe('invoke-result');

        expect(appRegistry.registerHeadlessTask).toHaveBeenCalledWith(
            'SolanaMobileWalletAdapterSessionBackgroundTask',
            expect.any(Function),
        );
        expect(mockCreateMobileWalletProxy).toHaveBeenCalledWith('v1', expect.any(Function));
        expect(nativeModule.endSession).toHaveBeenCalledTimes(1);
        expect(nativeModule.invoke).toHaveBeenCalledWith('sign_messages', {
            addresses: [],
            payloads: [],
        });
        expect(nativeModule.startSession).toHaveBeenCalledWith(config);
    });

    it('ends the native session when the callback throws after connection', async () => {
        const nativeModule = createNativeModule();
        const thrownError = new Error('callback failed');
        mockCreateMobileWalletProxy.mockReturnValue(WALLET);

        const { transact } = await importReactNativeTransact({ nativeModule });

        await expect(
            transact(() => {
                throw thrownError;
            }),
        ).rejects.toBe(thrownError);

        expect(nativeModule.endSession).toHaveBeenCalledTimes(1);
    });

    it('does not end the native session when startSession fails before connection', async () => {
        const nativeModule = createNativeModule();
        nativeModule.startSession.mockRejectedValue(
            Object.assign(new Error('native wallet missing'), {
                code: SolanaMobileWalletAdapterErrorCode.ERROR_WALLET_NOT_FOUND,
            }),
        );

        const { transact } = await importReactNativeTransact({ nativeModule });

        await expect(transact(vi.fn())).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_WALLET_NOT_FOUND,
                message: 'Found no installed wallet that supports the mobile wallet protocol.',
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
        expect(nativeModule.endSession).not.toHaveBeenCalled();
    });

    it('maps native JSON-RPC errors thrown by invoke', async () => {
        const nativeModule = createNativeModule();
        let invokePromise: Promise<unknown> | undefined;
        mockCreateMobileWalletProxy.mockImplementation((_protocolVersion, requestHandler) => {
            const pendingInvokePromise = requestHandler('sign_messages', {
                addresses: [],
                payloads: [],
            });
            invokePromise = pendingInvokePromise;
            void pendingInvokePromise.catch(() => undefined);
            return WALLET;
        });
        nativeModule.invoke.mockImplementation(() => {
            throw Object.assign(new Error('not signed'), {
                code: 'JSON_RPC_ERROR',
                userInfo: { jsonRpcErrorCode: -3 },
            });
        });

        const { transact } = await importReactNativeTransact({ nativeModule });

        await expect(transact(() => 'done')).resolves.toBe('done');
        if (!invokePromise) {
            throw new Error('Expected invoke promise to be captured');
        }
        await expect(invokePromise).rejects.toEqual(
            expect.objectContaining({
                code: -3,
                jsonRpcMessageId: 0,
                message: 'not signed',
                name: 'SolanaMobileWalletAdapterProtocolError',
            }),
        );
        expect(nativeModule.endSession).toHaveBeenCalledTimes(1);
    });

    it('throws the platform compatibility error on non-Android platforms', async () => {
        const { transact } = await importReactNativeTransact({
            nativeModule: null,
            platformOS: 'ios',
        });

        await expect(transact(vi.fn())).rejects.toThrow(
            'The package `solana-mobile-wallet-adapter-protocol` is only compatible with React Native Android',
        );
    });

    it('throws the linking error on Android when the native module is missing', async () => {
        const { transact } = await importReactNativeTransact({
            nativeModule: null,
            platformOS: 'android',
        });

        await expect(transact(vi.fn())).rejects.toThrow(
            "The package 'solana-mobile-wallet-adapter-protocol' doesn't seem to be linked",
        );
    });
});

type NativeModuleMock = ReturnType<typeof createNativeModule>;

function createNativeModule() {
    return {
        endSession: vi.fn().mockResolvedValue(undefined),
        invoke: vi.fn(),
        startSession: vi.fn().mockResolvedValue({ protocol_version: 'v1' }),
    };
}

async function importReactNativeTransact({
    nativeModule = createNativeModule(),
    platformOS = 'android',
}: {
    nativeModule?: NativeModuleMock | null;
    platformOS?: string;
} = {}) {
    vi.resetModules();

    const appRegistry = {
        registerHeadlessTask: vi.fn(),
    };
    const platform = { OS: platformOS };

    vi.doMock('react-native', () => ({
        AppRegistry: appRegistry,
        Platform: platform,
    }));
    vi.doMock('../src/codegenSpec/NativeSolanaMobileWalletAdapter.js', () => ({
        default: nativeModule,
    }));
    vi.doMock('../src/createMobileWalletProxy.js', () => ({
        default: mockCreateMobileWalletProxy,
    }));

    const module = await import('../src/__forks__/react-native/transact.js');
    return {
        appRegistry,
        transact: module.transact,
    };
}
