import {Keypair} from '@solana/web3.js';
import React from 'react';
import {StyleSheet, View} from 'react-native';
import {Button, Divider, Text} from 'react-native-paper';

import {
  SignMessagesRequest,
  SignPayloadsRequest,
  SignTransactionsRequest,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import {SolanaSigningUseCase} from '../utils/SolanaSigningUseCase';
import {useWallet} from '../components/WalletProvider';
import BottomsheetHeader from '../components/BottomsheetHeader';

const signPayloads = (wallet: Keypair, request: SignPayloadsRequest) => {
  const valid: boolean[] = request.payloads.map(_ => {
    return true;
  });

  let signedPayloads: Uint8Array[];
  if (request instanceof SignTransactionsRequest) {
    signedPayloads = request.payloads.map((numArray, index) => {
      try {
        return SolanaSigningUseCase.signTransaction(
          new Uint8Array(numArray),
          wallet,
        );
      } catch (e) {
        console.warn(`Transaction ${index} is not a valid Solana transaction`);
        valid[index] = false;
        return new Uint8Array();
      }
    });
  } else if (request instanceof SignMessagesRequest) {
    signedPayloads = request.payloads.map(numArray => {
      return SolanaSigningUseCase.signMessage(new Uint8Array(numArray), wallet);
    });
  } else {
    console.warn('Invalid payload screen request type');
    return;
  }

  // If all valid, then call complete request
  if (!valid.includes(false)) {
    request.completeWithSignedPayloads(signedPayloads);
  } else {
    request.completeWithInvalidPayloads(valid);
  }
};

interface SignPayloadsScreenProps {
  request: SignPayloadsRequest;
}

// this view is basically the same as AuthenticationScreen.
// Should either combine them or pull common code to base abstraction
export default function SignPayloadsScreen({request}: SignPayloadsScreenProps) {
  const {wallet} = useWallet();

  // We should always have an available keypair here.
  if (!wallet) {
    throw new Error('Wallet is null or undefined');
  }

  return (
    <View>
      <BottomsheetHeader title={'Sign payloads'} />
      <Divider style={styles.spacer} />
      <View style={styles.contentSection}>
        <Text style={styles.content}>
          numPayloads: {request.payloads.length}
        </Text>
      </View>
      <View style={styles.buttonGroup}>
        <Button
          style={styles.actionButton}
          onPress={() => {
            signPayloads(wallet, request);
          }}
          mode="contained">
          Sign
        </Button>
        <Button style={styles.actionButton} mode="outlined">
          Reject
        </Button>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
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
});
