import {
  SolanaMobileWalletAdapter,
  createDefaultAuthorizationResultCache,
} from '@solana-mobile/wallet-adapter-mobile';
import {Provider as PaperProvider} from 'react-native-paper';
import React, {ReactNode, useMemo} from 'react';
import {clusterApiUrl} from '@solana/web3.js';
import {WalletAdapterNetwork} from '@solana/wallet-adapter-base';
import {ConnectionProvider, WalletProvider} from '@solana/wallet-adapter-react';

const DEVNET_ENDPOINT = /*#__PURE__*/ clusterApiUrl(
  WalletAdapterNetwork.Devnet,
);

type Props = {
  children: ReactNode;
};

export default function Shell({children}: Props) {
  const wallets = useMemo(
    () => [
      new SolanaMobileWalletAdapter({
        appIdentity: {
          name: 'React Native dApp',
        },
        authorizationResultCache: createDefaultAuthorizationResultCache(),
      }),
    ],
    [],
  );
  return (
    <ConnectionProvider endpoint={DEVNET_ENDPOINT}>
      <WalletProvider wallets={wallets}>
        <PaperProvider>{children}</PaperProvider>
      </WalletProvider>
    </ConnectionProvider>
  );
}
