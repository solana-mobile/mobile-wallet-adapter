import { AuthorizationResult } from '@solana-mobile/mobile-wallet-adapter-protocol';
import { AuthorizationResultCache } from '../../adapter';
import AsyncStorage from '@react-native-async-storage/async-storage';

const CACHE_KEY = 'SolanaMobileWalletAdapterDefaultAuthorizationCache';

export default function createDefaultAuthorizationResultCache(): AuthorizationResultCache {
    return {
        async clear() {
            try {
                await AsyncStorage.removeItem(CACHE_KEY);
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async get() {
            try {
                return (
                    (JSON.parse((await AsyncStorage.getItem(CACHE_KEY)) as string) as AuthorizationResult) || undefined
                );
                // eslint-disable-next-line no-empty
            } catch {}
        },
        async set(authorizationResult: AuthorizationResult) {
            try {
                await AsyncStorage.setItem(CACHE_KEY, JSON.stringify(authorizationResult));
                // eslint-disable-next-line no-empty
            } catch {}
        },
    };
}
