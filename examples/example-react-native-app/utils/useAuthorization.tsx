import AsyncStorage from '@react-native-async-storage/async-storage';
import {PublicKey} from '@solana/web3.js';
import {
  AuthorizationResult,
  AuthorizeAPI,
  DeauthorizeAPI,
  ReauthorizeAPI,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
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

export const APP_IDENTITY = {
  name: 'React Native dApp',
};

export default function useAuthorization() {
  const {data: cachedAuthorization, mutate} = useSWR(
    STORAGE_KEY,
    authorizationFetcher,
    {
      suspense: true,
    },
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
  const publicKey = useMemo(
    () =>
      cachedAuthorization
        ? new PublicKey(cachedAuthorization.pub_key)
        : undefined,
    [cachedAuthorization],
  );
  const authorizeSession = useCallback(
    async (wallet: AuthorizeAPI & ReauthorizeAPI) => {
      let freshAuthToken: string;
      let freshPublicKey: PublicKey;
      if (cachedAuthorization?.auth_token) {
        const reauthorizationResult = await wallet.reauthorize({
          auth_token: cachedAuthorization?.auth_token,
        });
        freshAuthToken = reauthorizationResult.auth_token;
        freshPublicKey = publicKey!;
      } else {
        const authorizationResult = await wallet.authorize({
          identity: APP_IDENTITY,
        });
        freshAuthToken = authorizationResult.auth_token;
        freshPublicKey = new PublicKey(authorizationResult.pub_key);
        setAuthorization(authorizationResult);
      }
      return {authToken: freshAuthToken, publicKey: freshPublicKey};
    },
    [cachedAuthorization, publicKey, setAuthorization],
  );
  const deauthorizeSession = useCallback(
    async (wallet: DeauthorizeAPI) => {
      if (cachedAuthorization?.auth_token == null) {
        return;
      }
      await wallet.deauthorize({auth_token: cachedAuthorization?.auth_token});
      setAuthorization(null);
    },
    [cachedAuthorization, setAuthorization],
  );
  return {
    authorizeSession,
    deauthorizeSession,
    publicKey,
  };
}
