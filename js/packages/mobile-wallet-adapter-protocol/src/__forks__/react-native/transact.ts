import { NativeModules, Platform } from 'react-native';

import { SolanaMobileWalletAdapterProtocolJsonRpcError } from '../../errors';
import { MobileWallet, WalletAssociationConfig } from '../../types';

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
                            if (e instanceof Error && (e as any).code === 'JSON_RPC_ERROR') {
                                const details = (e as any).userInfo as Readonly<{ jsonRpcErrorCode: number }>;
                                throw new SolanaMobileWalletAdapterProtocolJsonRpcError<any>(
                                    0 /* jsonRpcMessageId */,
                                    details.jsonRpcErrorCode,
                                    e.message,
                                );
                            }
                            throw e;
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
    } finally {
        if (didSuccessfullyConnect) {
            await SolanaMobileWalletAdapter.endSession();
        }
    }
}
