import {useWallet} from '@solana/wallet-adapter-react';
import React, {ComponentProps} from 'react';
import {Button} from 'react-native-paper';

import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<ComponentProps<typeof Button>>;

export default function DisconnectButton(props: Props) {
  const {connected, disconnect} = useWallet();
  const handleDisconnectClick = useGuardedCallback(disconnect, () => {}, [
    disconnect,
  ]);
  return (
    <Button {...props} disabled={!connected} onPress={handleDisconnectClick} />
  );
}
