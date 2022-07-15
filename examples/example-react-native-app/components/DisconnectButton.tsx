import React, {ComponentProps} from 'react';
import {Button} from 'react-native-paper';

import useAuthorization from '../utils/useAuthorization';

type Props = Readonly<ComponentProps<typeof Button>>;

export default function DisconnectButton(props: Props) {
  const {setAuthorization} = useAuthorization();
  return (
    <Button
      {...props}
      onPress={() => {
        setAuthorization(null);
      }}
    />
  );
}
