import { PublicKey } from '@solana/web3.js';
import React from 'react';
import {NativeModules, Platform, StyleSheet, View} from 'react-native';
import {Button, Divider, Text} from 'react-native-paper';

import FadeInView from './../components/FadeInView';

const SolanaMobileWalletAdapter =
    Platform.OS === 'android' && NativeModules.WalletLib
        ? NativeModules.WalletLib
        : new Proxy(
              {},
              {
                  get() {
                      throw new Error(
                          Platform.OS !== 'android'
                              ? 'The package `solana-mobile-wallet-adapter-protocol` is only compatible with React Native Android'
                              : LINKING_ERROR,
                      );
                  },
              },
          );

type Props = Readonly<{
  publicKey: PublicKey;
}>;

export default function AuthenticationScreen({publicKey}: Props) {
  const [visible, setIsVisible] = React.useState(true);

  // there has got to be a better way to reset the state, 
  // so it alwyas shows on render. I am react n00b 
  React.useEffect(() => {
    setIsVisible(true);
  });
  
  return (
      <FadeInView style={styles.container} shown={visible}>
        <Text variant="bodyLarge">
          Authorize The Things
        </Text>
        <Divider style={styles.spacer} />
        <View style={styles.buttonGroup}>
          <Button
            style={styles.actionButton}
            onPress = {() => {
              SolanaMobileWalletAdapter.authorizeDapp(Array.from(publicKey.toBytes()));
              setIsVisible(false);
            }}
            mode="contained">
            Authorize
          </Button>
          <Button
            style={styles.actionButton}
            mode="outlined">
            Decline
          </Button>
        </View>
      </FadeInView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
    backgroundColor: 'skyblue',
    justifyContent: 'space-between',
    borderTopLeftRadius: 15,
    borderTopRightRadius: 15,
  },
  shell: {
    height: '100%',
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
  }
});