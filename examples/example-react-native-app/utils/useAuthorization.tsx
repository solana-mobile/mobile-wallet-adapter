import {PublicKey} from '@solana/web3.js';
import {
  AuthorizationResult,
  AuthorizeAPI,
  AuthToken,
  Base64EncodedAddress,
  DeauthorizeAPI,
  ReauthorizeAPI,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import {toUint8Array} from 'js-base64';
import {useCallback} from 'react';
import useSWR from 'swr';

type Account = Readonly<{
  address: Base64EncodedAddress;
  authToken: AuthToken;
  publicKey: PublicKey;
}>;

const STORAGE_KEY = 'cachedAuthorization';

function getAccountFromAuthorizationResult(
  authorizationResult: AuthorizationResult,
): Account {
  const address = authorizationResult.addresses[0]; // TODO(#44): support multiple addresses
  return {
    address,
    authToken: authorizationResult.auth_token,
    publicKey: getPublicKeyFromAddress(address),
  };
}

function getPublicKeyFromAddress(address: Base64EncodedAddress): PublicKey {
  const publicKeyByteArray = toUint8Array(address);
  return new PublicKey(publicKeyByteArray);
}

export const APP_IDENTITY = {
  name: 'React Native dApp',
};

export default function useAuthorization() {
  const {data: account, mutate} = useSWR<Account | null | undefined>(
    STORAGE_KEY,
  );
  const authorizeSession = useCallback(
    async (wallet: AuthorizeAPI & ReauthorizeAPI) => {
      const authorizationResult = await (account
        ? wallet.reauthorize({
            auth_token: account.authToken,
          })
        : wallet.authorize({
            cluster: 'devnet',
            identity: APP_IDENTITY,
          }));
      return await mutate(
        getAccountFromAuthorizationResult(authorizationResult),
      );
    },
    [account, mutate],
  );
  const deauthorizeSession = useCallback(
    async (wallet: DeauthorizeAPI) => {
      if (account?.authToken == null) {
        return;
      }
      await wallet.deauthorize({auth_token: account?.authToken});
      mutate(null);
    },
    [account, mutate],
  );
  return {
    account: account ?? null,
    authorizeSession,
    deauthorizeSession,
  };
}
