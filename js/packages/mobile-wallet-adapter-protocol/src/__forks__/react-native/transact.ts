import { NativeModules, Platform } from 'react-native';

import { SolanaMobileWalletAdapterError, SolanaMobileWalletAdapterProtocolError } from '../../errors.js';
import { MobileWallet, WalletAssociationConfig } from '../../types.js';

type ReactNativeError = Error & { code?: string; userInfo?: Record<string, unknown> };

const LINKING_ERROR =
    `The package 'solana-mobile-wallet-adapter-protocol' doesn't seem to be linked. Make sure: \n\n` +
    '- You rebuilt the app after installing the package\n' +
    '- If you are using Lerna workspaces\n' +
    '  - You have added `@solana-mobile/mobile-wallet-adapter-protocol` as an explicit dependency, and\n' +
    '  - You have added `@solana-mobile/mobile-wallet-adapter-protocol` to the `nohoist` section of your package.json\n' +
    '- You are not using Expo managed workflow\n';

const SolanaMobileWalletAdapter =
    Platform.OS === 'android' && NativeModules.SolanaMobileWalletAdapter
        ? NativeModules.SolanaMobileWalletAdapter
        : new Proxy(
              {},
              {
                  get() {
                      throw new Error(
                          Platform.OS !== 'android'
                              ? 'The package `solana-mobile-wallet-adapter-protocol` is only compatible with React Native Android'
                              : LINKING_ERROR,
                      );
                  },
              },
          );

function getErrorMessage(e: ReactNativeError): string {
    switch (e.code) {
        case 'ERROR_WALLET_NOT_FOUND':
            return 'Found no installed wallet that supports the mobile wallet protocol.';
        default:
            return e.message;
    }
}

function handleError(e: any): never {
    if (e instanceof Error) {
        const reactNativeError: ReactNativeError = e;
        switch (reactNativeError.code) {
            case undefined:
                throw e;
            case 'JSON_RPC_ERROR': {
                const details = reactNativeError.userInfo as Readonly<{ jsonRpcErrorCode: number }>;
                throw new SolanaMobileWalletAdapterProtocolError(
                    0 /* jsonRpcMessageId */,
                    details.jsonRpcErrorCode,
                    e.message,
                );
            }
            default:
                throw new SolanaMobileWalletAdapterError<any>(
                    reactNativeError.code,
                    getErrorMessage(reactNativeError),
                    reactNativeError.userInfo,
                );
        }
    }
    throw e;
}

export async function transact<TReturn>(
    callback: (wallet: MobileWallet) => TReturn,
    config?: WalletAssociationConfig,
): Promise<TReturn> {
    let didSuccessfullyConnect = false;
    try {
        await SolanaMobileWalletAdapter.startSession(config);
        didSuccessfullyConnect = true;
        const wallet = new Proxy<MobileWallet>({} as MobileWallet, {
            get<TMethodName extends keyof MobileWallet>(target: MobileWallet, p: TMethodName) {
                if (target[p] == null) {
                    const method = p
                        .toString()
                        .replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`)
                        .toLowerCase();
                    target[p] = async function (params: Parameters<MobileWallet[TMethodName]>[0]) {
                        try {
                            return await SolanaMobileWalletAdapter.invoke(method, params);
                        } catch (e) {
                            return handleError(e);
                        }
                    } as MobileWallet[TMethodName];
                }
                return target[p];
            },
            defineProperty() {
                return false;
            },
            deleteProperty() {
                return false;
            },
        });
        return await callback(wallet);
    } catch (e) {
        return handleError(e);
    } finally {
        if (didSuccessfullyConnect) {
            await SolanaMobileWalletAdapter.endSession();
        }
    }
}
