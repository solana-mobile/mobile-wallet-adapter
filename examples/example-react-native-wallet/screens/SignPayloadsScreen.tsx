import { PublicKey, Keypair } from '@solana/web3.js';
import React, {useState, useEffect} from 'react';
import {BackHandler, NativeModules, Platform, StyleSheet, View} from 'react-native';
import {Button, Divider, Text} from 'react-native-paper';
import { MobileWalletAdapterServiceEventType } from '../App';
import { SolanaSigningUseCase } from '../utils/SolanaSigningUseCase';

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

type SignPayloadEvent = {
  type: MobileWalletAdapterServiceEventType.SignMessages | MobileWalletAdapterServiceEventType.SignTransactions;
  payloads: number[][];
}

type Props = Readonly<{
  wallet: Keypair | null;
  event: SignPayloadEvent;
}>;

// this view is basically the same as AuthenticationScreen. 
// Should either combine them or pull common code to base abstraction
export default function SignPayloadsScreen({wallet, event}: Props) {
  if (wallet === null) {
    return null
  }

  const [visible, setIsVisible] = useState(true);

  // there has got to be a better way to reset the state, 
  // so it alwyas shows on render. I am react n00b 
  useEffect(() => {
    setIsVisible(true);
  });


  return (
      <FadeInView style={styles.container} shown={visible}>
        <Text variant="bodyLarge">
          Sign The Transaction Things
        </Text>
        <Divider style={styles.spacer} />
        <View style={styles.buttonGroup}>
          <Button
            style={styles.actionButton}
            onPress = {() => {
              
              // TODO: move this code into a separate method
              const valid: boolean[] = event.payloads.map((numArray) => {
                return true
              })
              
              let signedPayloads;
              switch (event.type) {
                case MobileWalletAdapterServiceEventType.SignTransactions:
                  signedPayloads = event.payloads.map((numArray, index) => {
                    try {
                      return Array.from(SolanaSigningUseCase.signTransaction(new Uint8Array(numArray), wallet).signedPayload)
                    } catch (e) {
                      NativeModules.WalletLib.log(`Transaction ${index} is not a valid Solana transaction`);
                      valid[index] = false
                      return new Uint8Array([])
                    }
                  });
                  break;
                case MobileWalletAdapterServiceEventType.SignMessages:
                  signedPayloads = event.payloads.map((numArray) => {
                    return Array.from(SolanaSigningUseCase.signMessage(new Uint8Array(numArray), wallet).signedPayload)
                  });
              }

              // If all valid, then call complete request
              if (!valid.includes(false)) {
                SolanaMobileWalletAdapter.completeSignPayloadsRequest(Array.from(signedPayloads));
              } else {
                SolanaMobileWalletAdapter.completeWithInvalidPayloads(valid);
              }
              
              setIsVisible(false);
            }}
            mode="contained">
            Sign
          </Button>
          <Button
            style={styles.actionButton}
            mode="outlined">
            Reject
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