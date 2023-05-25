import React from 'react';
import {StyleSheet, View} from 'react-native';
import {Button, Divider, Text} from 'react-native-paper';
import {
  AuthorizeDappRequest,
  MWARequestFailReason,
  resolve,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import {useWallet} from '../components/WalletProvider';
import MWABottomsheetHeader from '../components/MWABottomsheetHeader';

interface AuthenticationScreenProps {
  request: AuthorizeDappRequest;
}

export default function AuthenticationScreen({
  request,
}: AuthenticationScreenProps) {
  const {wallet} = useWallet();

  // We should always have an available keypair here.
  if (!wallet) {
    throw new Error('Wallet is null or undefined');
  }

  return (
    <View>
      <MWABottomsheetHeader
        title={'Authorize Dapp'}
        cluster={request.cluster}
        appIdentity={request.appIdentity}
      />
      <View style={styles.buttonGroup}>
        <Button
          style={styles.actionButton}
          onPress={() => {
            resolve(request, {
              publicKey: wallet.publicKey.toBytes(),
              accountLabel: 'Backpack',
            });
          }}
          mode="contained">
          Authorize
        </Button>
        <Button
          style={styles.actionButton}
          onPress={() => {
            resolve(request, {failReason: MWARequestFailReason.UserDeclined});
          }}
          mode="outlined">
          Decline
        </Button>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  buttonGroup: {
    display: 'flex',
    flexDirection: 'row',
    width: '100%',
  },
  actionButton: {
    flex: 1,
    marginEnd: 8,
  },
});
