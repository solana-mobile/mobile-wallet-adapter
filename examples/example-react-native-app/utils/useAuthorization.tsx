import AsyncStorage from '@react-native-async-storage/async-storage';
import {PublicKey} from '@solana/web3.js';
import {
  AuthorizationResult,
  AuthorizeAPI,
  Base64EncodedAddress,
  DeauthorizeAPI,
  ReauthorizeAPI,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import {toUint8Array} from 'js-base64';
import {useCallback} from 'react';
import useSWR from 'swr';

const STORAGE_KEY = 'cachedAuthorization';

function getDataFromAuthorizationResult(
  authorizationResult: AuthorizationResult,
) {
  return {
    authorization: authorizationResult,
    publicKey: getPublicKeyFromAuthorizationResult(authorizationResult),
  };
}

function getPublicKeyFromAuthorizationResult(
  authorizationResult: AuthorizationResult,
): PublicKey {
  return getPublicKeyFromAddress(
    // TODO(#44): support multiple addresses
    authorizationResult.addresses[0],
  );
}

async function authorizationFetcher(storageKey: string) {
  try {
    const serializedValue = await AsyncStorage.getItem(storageKey);
    if (!serializedValue) {
      return null;
    }
    const authorization = JSON.parse(serializedValue) as AuthorizationResult;
    return getDataFromAuthorizationResult(authorization);
  } catch {
    // Presume the data in storage is corrupt and erase it.
    await AsyncStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

function getPublicKeyFromAddress(address: Base64EncodedAddress): PublicKey {
  const publicKeyByteArray = toUint8Array(address);
  return new PublicKey(publicKeyByteArray);
}

export const APP_IDENTITY = {
  name: 'React Native dApp',
};

export default function useAuthorization() {
  const {data, mutate} = useSWR(STORAGE_KEY, authorizationFetcher, {
    suspense: true,
  });
  const setAuthorization = useCallback(
    (authorizationResult: AuthorizationResult | null) => {
      mutate(
        async () => {
          if (authorizationResult) {
            await AsyncStorage.setItem(
              STORAGE_KEY,
              JSON.stringify(authorizationResult),
            );
            return getDataFromAuthorizationResult(authorizationResult);
          } else {
            await AsyncStorage.removeItem(STORAGE_KEY);
            return null;
          }
        },
        {
          optimisticData: authorizationResult
            ? getDataFromAuthorizationResult(authorizationResult)
            : null,
        },
      );
    },
    [mutate],
  );
  const authorizeSession = useCallback(
    async (wallet: AuthorizeAPI & ReauthorizeAPI) => {
      const authorizationResult = await (data
        ? wallet.reauthorize({
            auth_token: data.authorization.auth_token,
          })
        : wallet.authorize({
            cluster: 'devnet',
            identity: APP_IDENTITY,
          }));
      setAuthorization(authorizationResult);
      return getPublicKeyFromAuthorizationResult(authorizationResult);
    },
    [data, setAuthorization],
  );
  const deauthorizeSession = useCallback(
    async (wallet: DeauthorizeAPI) => {
      if (data?.authorization.auth_token == null) {
        return;
      }
      await wallet.deauthorize({auth_token: data.authorization.auth_token});
      setAuthorization(null);
    },
    [data?.authorization.auth_token, setAuthorization],
  );
  return {
    authorizeSession,
    deauthorizeSession,
    publicKey: data?.publicKey ?? null,
  };
}
