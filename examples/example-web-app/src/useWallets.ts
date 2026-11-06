/** React binding for the Wallet Standard registry. */
import { getWallets } from '@wallet-standard/app';
import type { Wallet } from '@wallet-standard/base';
import { useSyncExternalStore } from 'react';

const { get, on } = getWallets();

let snapshot: readonly Wallet[] = get();
const listeners = new Set<() => void>();

function update() {
    snapshot = get();
    listeners.forEach((listener) => listener());
}

// Subscribed for the module's lifetime, so wallets registered before the first render are not missed.
on('register', update);
on('unregister', update);

function subscribe(listener: () => void): () => void {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

export function useWallets(): readonly Wallet[] {
    return useSyncExternalStore(subscribe, () => snapshot);
}
