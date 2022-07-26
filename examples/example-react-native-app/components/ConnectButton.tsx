import {transact} from '@solana-mobile/mobile-wallet-adapter-protocol-web3js';
import React, {ComponentProps, useState} from 'react';
import {Button} from 'react-native-paper';

import useAuthorization from '../utils/useAuthorization';
import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<ComponentProps<typeof Button>>;

export default function ConnectButton(props: Props) {
  const {authorizeSession} = useAuthorization();
  const [authorizationInProgress, setAuthorizationInProgress] = useState(false);
  const handleConnectPress = useGuardedCallback(async () => {
    try {
      if (authorizationInProgress) {
        return;
      }
      setAuthorizationInProgress(true);
      await transact(async wallet => {
        await authorizeSession(wallet);
      });
    } finally {
      setAuthorizationInProgress(false);
    }
  }, []);
  return (
    <Button
      {...props}
      disabled={authorizationInProgress}
      onPress={handleConnectPress}
    />
  );
}
