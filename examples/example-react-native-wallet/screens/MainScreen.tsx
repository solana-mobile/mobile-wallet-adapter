import AsyncStorage from '@react-native-async-storage/async-storage';
import {Keypair} from '@solana/web3.js';
import React from 'react';
import {View, StyleSheet} from 'react-native';
import {Appbar, Button, Text} from 'react-native-paper';
import {encode} from 'bs58';

const ASYNC_STORAGE_KEY: string = '@reactnativefakewallet_keypair_key';

type EncodedKeypair = {
  publicKeyBase58: string;
  secretKeyBase58: string;
};

const encodeKeypair = (keypair: Keypair): EncodedKeypair => {
  return {
    publicKeyBase58: keypair.publicKey.toBase58(),
    secretKeyBase58: encode(keypair.secretKey),
  };
};

export default function MainScreen() {
  return (
    <>
      <Appbar.Header elevated mode="center-aligned">
        <Appbar.Content title="React Native Wallet" />
      </Appbar.Header>
      <View style={styles.container}>
        <Text>I'm a Wallet!</Text>
        <Button
          onPress={async () => {
            const keypair = await Keypair.generate();
            console.log('Keypair reset to: ' + keypair.publicKey);
            await AsyncStorage.setItem(
              ASYNC_STORAGE_KEY,
              JSON.stringify(encodeKeypair(keypair)),
            );
          }}>
          Reset wallet keypair
        </Button>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    height: '90%',
    justifyContent: 'center',
    alignItems: 'center',
  },
});
