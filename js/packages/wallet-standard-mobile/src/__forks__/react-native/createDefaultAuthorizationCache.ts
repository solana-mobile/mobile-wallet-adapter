import AsyncStorage from '@react-native-async-storage/async-storage';

import { Authorization, AuthorizationCache } from '../../wallet.js';
import base58 from 'bs58';

const CACHE_KEY = 'SolanaMobileWalletAdapterWalletStandardDefaultAuthorizationCache';

export default function createDefaultAuthorizationCache(): AuthorizationCache {
    return {
        async clear() {
            try {
                await AsyncStorage.removeItem(CACHE_KEY);
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async get() {
            try {
                const parsed = JSON.parse((await AsyncStorage.getItem(CACHE_KEY)) as string) as Authorization;
                if (parsed && parsed.accounts) {
                    const parsedAccounts = parsed.accounts.map((account) => {
                        return {
                            ...account,
                            publicKey: 'publicKey' in account
                                ? new Uint8Array(Object.values(account.publicKey)) // Rebuild publicKey for WalletAccount
                                : base58.decode(account.address), // Fallback, get publicKey from address
                        }
                    })
                    return { ...parsed, accounts: parsedAccounts }
                } else return parsed || undefined;
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async set(authorizationResult: Authorization) {
            try {
                await AsyncStorage.setItem(CACHE_KEY, JSON.stringify(authorizationResult));
                // eslint-disable-next-line no-empty
            } catch {}
        },
    };
}
