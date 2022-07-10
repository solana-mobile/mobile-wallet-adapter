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

export default async function withLocalWallet<TReturn>(
    callback: (wallet: MobileWallet) => TReturn,
    config?: WalletAssociationConfig,
): Promise<TReturn> {
    try {
        await SolanaMobileWalletAdapter.startSession(config);
        return await callback(async (method, params) => {
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
        });
    } finally {
        await SolanaMobileWalletAdapter.endSession();
    }
}
