import {
    RemoteWalletAssociationConfig,
    startRemoteScenario as baseStartRemoteScenario,
    TerminateSessionAPI,
} from '@solana-mobile/mobile-wallet-adapter-protocol';

import { augmentWalletAPI, KitMobileWallet, KitScenario } from './transact.js';

export interface KitRemoteMobileWallet extends KitMobileWallet, TerminateSessionAPI {}

export type KitRemoteScenario = KitScenario &
    Readonly<{
        associationUrl: URL;
    }>;

export async function startRemoteScenario(config: RemoteWalletAssociationConfig): Promise<KitRemoteScenario> {
    const { wallet, close, associationUrl } = await baseStartRemoteScenario(config);
    const augmentedPromise = wallet.then((wallet) => {
        return augmentWalletAPI(wallet);
    });
    return { wallet: augmentedPromise, close, associationUrl };
}
