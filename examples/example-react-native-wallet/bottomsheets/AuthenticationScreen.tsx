import React from 'react';
import {StyleSheet, View} from 'react-native';
import {Button, Divider, Text} from 'react-native-paper';
import {AuthorizeDappRequest} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import {useWallet} from '../components/WalletProvider';
import BottomsheetHeader from '../components/BottomsheetHeader';

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
      <BottomsheetHeader
        title={'Authorize Dapp'}
        iconSource={
          request.iconRelativeUri && request.identityUri
            ? {
                uri: new URL(
                  request.iconRelativeUri,
                  request.identityUri,
                ).toString(),
              }
            : require('../img/unknownapp.jpg')
        }
      />
      <Divider style={styles.spacer} />
      <View style={styles.contentSection}>
        <Text style={styles.content}>Cluster: {request.cluster}</Text>
        <Text style={styles.content}>identityName: {request.identityName}</Text>
        <Text style={styles.content}>identityUri: {request.identityUri}</Text>
      </View>
      <View style={styles.buttonGroup}>
        <Button
          style={styles.actionButton}
          onPress={() => {
            request.completeWithAuthorize(
              wallet.publicKey.toBytes(),
              'Backpack',
              null,
              null,
            );
          }}
          mode="contained">
          Authorize
        </Button>
        <Button
          style={styles.actionButton}
          onPress={() => {
            request.completeWithDecline();
          }}
          mode="outlined">
          Decline
        </Button>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  shell: {
    height: '100%',
  },
  icon: {
    width: 75,
    height: 75,
  },
  contentSection: {
    display: 'flex',
    flexDirection: 'column',
    paddingBottom: 16,
  },
  content: {
    textAlign: 'left',
    color: 'black',
    fontSize: 18,
  },
  spacer: {
    marginVertical: 16,
    width: '100%',
  },
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
