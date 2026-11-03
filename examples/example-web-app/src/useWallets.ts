/** React binding for the Wallet Standard registry. */
import { getWallets } from '@wallet-standard/app';
import type { Wallet } from '@wallet-standard/base';
import { useSyncExternalStore } from 'react';

const { get, on } = getWallets();

let snapshot: readonly Wallet[] = get();

function subscribe(callback: () => void): () => void {
    // Catch up on wallets registered between module evaluation and the first subscription.
    snapshot = get();
    const update = () => {
        snapshot = get();
        callback();
    };
    const offs = [on('register', update), on('unregister', update)];
    return () => offs.forEach((off) => off());
}

export function useWallets(): readonly Wallet[] {
    return useSyncExternalStore(subscribe, () => snapshot);
}
