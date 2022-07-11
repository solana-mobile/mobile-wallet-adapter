import {useWallet} from '@solana/wallet-adapter-react';
import React, {ComponentProps} from 'react';
import {Button} from 'react-native-paper';

import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<ComponentProps<typeof Button>>;

export default function ConnectButton(props: Props) {
  const {connect, connected} = useWallet();
  const handleConnectPress = useGuardedCallback(connect, () => {}, [connect]);
  return (
    <Button {...props} disabled={connected} onPress={handleConnectPress} />
  );
}
