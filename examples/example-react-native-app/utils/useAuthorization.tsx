import AsyncStorage from '@react-native-async-storage/async-storage';
import {PublicKey} from '@solana/web3.js';
import {AuthorizationResult} from '@solana-mobile/mobile-wallet-adapter-protocol';
import {useCallback, useMemo} from 'react';
import useSWR from 'swr';

const STORAGE_KEY = 'cachedAuthorization';

async function authorizationFetcher(
  storageKey: string,
): Promise<AuthorizationResult | null> {
  try {
    const serializedValue = await AsyncStorage.getItem(storageKey);
    if (!serializedValue) {
      return null;
    }
    return JSON.parse(serializedValue) as AuthorizationResult;
  } catch {
    return null;
  }
}

export default function useAuthorization() {
  const {data: authorization, mutate} = useSWR(
    STORAGE_KEY,
    authorizationFetcher,
    {
      suspense: true,
    },
  );
  const publicKey = useMemo(
    () => (authorization ? new PublicKey(authorization.pub_key) : null),
    [authorization],
  );
  const setAuthorization = useCallback(
    (authorizationResult: AuthorizationResult | null) => {
      mutate(
        async () => {
          if (authorizationResult) {
            await AsyncStorage.setItem(
              STORAGE_KEY,
              JSON.stringify(authorizationResult),
            );
          } else {
            await AsyncStorage.removeItem(STORAGE_KEY);
          }
          return authorizationResult ?? undefined;
        },
        {optimisticData: authorizationResult},
      );
    },
    [mutate],
  );
  return {
    authorization:
      authorization && publicKey
        ? {
            ...authorization,
            publicKey,
          }
        : null,
    setAuthorization,
  };
}
