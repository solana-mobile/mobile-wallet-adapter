import {
    RemoteWalletAssociationConfig,
    startRemoteScenario as baseStartRemoteScenario,
    TerminateSessionAPI,
} from '@solana-mobile/mobile-wallet-adapter-protocol';

import { augmentWalletAPI, Web3MobileWallet, Web3Scenario } from './transact.js';

export interface Web3RemoteMobileWallet extends Web3MobileWallet, TerminateSessionAPI {}

export type Web3RemoteScenario = Web3Scenario &
    Readonly<{
        associationUrl: URL;
    }>;

export async function startRemoteScenario(config: RemoteWalletAssociationConfig): Promise<Web3RemoteScenario> {
    const { wallet, close, associationUrl } = await baseStartRemoteScenario(config);
    const augmentedPromise = wallet.then((wallet) => {
        return augmentWalletAPI(wallet);
    });
    return { wallet: augmentedPromise, close, associationUrl };
}
