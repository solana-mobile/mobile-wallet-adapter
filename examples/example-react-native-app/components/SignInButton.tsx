import { transact } from '@solana-mobile/mobile-wallet-adapter-protocol-kit';
import React, { ComponentProps } from 'react';
import { Button } from 'react-native-paper';

import useAuthorization from '../utils/useAuthorization';

type Props = Readonly<ComponentProps<typeof Button>>;

export default function SignInButton(props: Props) {
  const { authorizeSessionWithSignIn } = useAuthorization();
  return (
    <Button
      {...props}
      onPress={() => {
        transact(async wallet => {
          await authorizeSessionWithSignIn(wallet);
        });
      }}
    />
  );
}
