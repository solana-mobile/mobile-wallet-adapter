import type { TransactionVersion } from '@solana/web3.js';
import type { IdentifierArray } from '@wallet-standard/core';
import { NativeModules, Platform } from 'react-native';

import { SolanaMWAWalletLibError, SolanaMWAWalletLibErrorCode } from './errors.js';

const LINKING_ERROR =
    `The package 'solana-mobile-wallet-adapter-walletlib' doesn't seem to be linked. Make sure: \n\n` +
    '- You rebuilt the app after installing the package\n' +
    '- If you are using Lerna workspaces\n' +
    '  - You have added `@solana-mobile/mobile-wallet-adapter-walletlib` as an explicit dependency, and\n' +
    '  - You have added `@solana-mobile/mobile-wallet-adapter-walletlib` to the `nohoist` section of your package.json\n' +
    '- You are not using Expo managed workflow\n';

const SolanaMobileWalletAdapterWalletLib =
    Platform.OS === 'android' && NativeModules.SolanaMobileWalletAdapterWalletLib
        ? NativeModules.SolanaMobileWalletAdapterWalletLib
        : new Proxy(
              {},
              {
                  get() {
                      throw new Error(
                          Platform.OS !== 'android'
                              ? 'The package `solana-mobile-wallet-adapter-walletlib` is only compatible with React Native Android'
                              : LINKING_ERROR,
                      );
                  },
              },
          );

type ReactNativeError = Error & { code?: string; userInfo?: Record<string, unknown> };

function handleError(e: any): never {
    if (e instanceof Error) {
        const { code, message } = e as ReactNativeError;
        if (code && code in SolanaMWAWalletLibErrorCode) {
            throw new SolanaMWAWalletLibError(code as SolanaMWAWalletLibErrorCode, message);
        }
    }
    throw e;
}

export type MWASessionId = string;

export interface MobileWalletAdapterConfig {
    maxTransactionsPerSigningRequest: number;
    maxMessagesPerSigningRequest: number;
    supportedTransactionVersions: Array<TransactionVersion>;
    noConnectionWarningTimeoutMs: number;
    optionalFeatures: IdentifierArray;
}

export async function initializeMobileWalletAdapterSession(
    walletName: string,
    config: MobileWalletAdapterConfig,
): Promise<MWASessionId> {
    try {
        return await initializeScenario(walletName, config);
    } catch (e) {
        handleError(e);
    }
}

// Create Scenario and establish session with dapp
function initializeScenario(walletName: string, walletConfig: MobileWalletAdapterConfig): Promise<MWASessionId> {
    return SolanaMobileWalletAdapterWalletLib.createScenario(walletName, JSON.stringify(walletConfig));
}
