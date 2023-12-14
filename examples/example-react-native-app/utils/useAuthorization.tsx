import {PublicKey} from '@solana/web3.js';
import {
  Account as AuthorizedAccount,
  AuthorizationResult,
  AuthorizeAPI,
  AuthToken,
  Base64EncodedAddress,
  DeauthorizeAPI,
  ReauthorizeAPI,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import {toUint8Array} from 'js-base64';
import {useCallback, useMemo} from 'react';
import useSWR from 'swr';

export type Account = Readonly<{
  address: Base64EncodedAddress;
  label?: string;
  publicKey: PublicKey;
}>;

type Authorization = Readonly<{
  accounts: Account[];
  authToken: AuthToken;
  selectedAccount: Account;
}>;

function getAccountFromAuthorizedAccount(account: AuthorizedAccount): Account {
  return {
    ...account,
    publicKey: getPublicKeyFromAddress(account.address),
  };
}

function getAuthorizationFromAuthorizationResult(
  authorizationResult: AuthorizationResult,
  previouslySelectedAccount?: Account,
): Authorization {
  let selectedAccount: Account;
  if (
    // We have yet to select an account.
    previouslySelectedAccount == null ||
    // The previously selected account is no longer in the set of authorized addresses.
    !authorizationResult.accounts.some(
      ({address}) => address === previouslySelectedAccount.address,
    )
  ) {
    const firstAccount = authorizationResult.accounts[0];
    selectedAccount = getAccountFromAuthorizedAccount(firstAccount);
  } else {
    selectedAccount = previouslySelectedAccount;
  }
  return {
    accounts: authorizationResult.accounts.map(getAccountFromAuthorizedAccount),
    authToken: authorizationResult.auth_token,
    selectedAccount,
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
  const {data: authorization, mutate: setAuthorization} = useSWR<
    Authorization | null | undefined
  >('authorization');
  const handleAuthorizationResult = useCallback(
    async (
      authorizationResult: AuthorizationResult,
    ): Promise<Authorization> => {
      const nextAuthorization = getAuthorizationFromAuthorizationResult(
        authorizationResult,
        authorization?.selectedAccount,
      );
      await setAuthorization(nextAuthorization);
      return nextAuthorization;
    },
    [authorization, setAuthorization],
  );
  const authorizeSession = useCallback(
    async (wallet: AuthorizeAPI) => {
      const authorizationResult = await wallet.authorize(
        {
          chain: 'solana:devnet',
          identity: APP_IDENTITY,
          auth_token: authorization ? authorization.authToken : undefined,
        }
      );
      return (await handleAuthorizationResult(authorizationResult))
        .selectedAccount;
    },
    [authorization, handleAuthorizationResult],
  );
  const authorizeSessionWithSignIn = useCallback(
    async (wallet: AuthorizeAPI) => {
      const authorizationResult = await wallet.authorize(
        {
          chain: 'solana:devnet',
          identity: APP_IDENTITY,
          // used to test sign in with solana
          // the dapp should also verify the signature returned in the authorizationResult
          sign_in_payload: {
            domain: 'solanamobile.com',
            statement: 'Sign into React Native Sample App',
            uri: 'https://solanamobile.com',
          }
        }
      );
      return (await handleAuthorizationResult(authorizationResult))
        .selectedAccount;
    },
    [handleAuthorizationResult],
  );
  const deauthorizeSession = useCallback(
    async (wallet: DeauthorizeAPI) => {
      if (authorization?.authToken == null) {
        return;
      }
      await wallet.deauthorize({auth_token: authorization.authToken});
      setAuthorization(null);
    },
    [authorization, setAuthorization],
  );
  const onChangeAccount = useCallback(
    (nextSelectedAccount: Account) => {
      setAuthorization(currentAuthorization => {
        if (
          !currentAuthorization?.accounts.some(
            ({address}) => address === nextSelectedAccount.address,
          )
        ) {
          throw new Error(
            `${nextSelectedAccount.address} is not one of the available addresses`,
          );
        }
        return {
          ...currentAuthorization,
          selectedAccount: nextSelectedAccount,
        };
      });
    },
    [setAuthorization],
  );
  return useMemo(
    () => ({
      accounts: authorization?.accounts ?? null,
      authorizeSession,
      authorizeSessionWithSignIn,
      deauthorizeSession,
      onChangeAccount,
      selectedAccount: authorization?.selectedAccount ?? null,
    }),
    [authorization, authorizeSession, authorizeSessionWithSignIn, deauthorizeSession, onChangeAccount],
  );
}
