import {Keypair} from '@solana/web3.js';
import React from 'react';
import {NativeModules, StyleSheet, View} from 'react-native';
import {Button, Divider} from 'react-native-paper';
import {SignAndSendTransactionsRequest} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import {SolanaSigningUseCase} from '../utils/SolanaSigningUseCase';
import {
  SendTransactionsUseCase,
  SendTransactionsError,
} from '../utils/SendTransactionsUseCase';
import {useWallet} from '../components/WalletProvider';
import BottomsheetHeader from '../components/BottomsheetHeader';

const signAndSendTransactions = async (
  wallet: Keypair,
  request: SignAndSendTransactionsRequest,
) => {
  const valid: boolean[] = request.payloads.map(_ => {
    return true;
  });
  let signedTransactions = request.payloads.map((numArray, index) => {
    try {
      return SolanaSigningUseCase.signTransaction(
        new Uint8Array(numArray),
        wallet,
      );
    } catch (e) {
      NativeModules.WalletLib.log(
        `Transaction ${index} is not a valid Solana transaction`,
      );
      valid[index] = false;
      return new Uint8Array([]);
    }
  });
  // If invalid, then fail the request
  if (valid.includes(false)) {
    request.completeWithInvalidSignatures(valid);
    return;
  }
  try {
    const signatures = await SendTransactionsUseCase.sendSignedTransactions(
      signedTransactions,
      request.minContextSlot ? Number(request.minContextSlot) : undefined,
    );
    request.completeWithSignatures(signatures);
  } catch (error) {
    console.log(`Error during signAndSendTransactions: ${error}`);
    if (error instanceof SendTransactionsError) {
      request.completeWithInvalidSignatures(error.valid);
    } else {
      throw error;
    }
  }
};

interface SignAndSendTransactionsScreenProps {
  request: SignAndSendTransactionsRequest;
}

export default function SignAndSendTransactionsScreen({
  request,
}: SignAndSendTransactionsScreenProps) {
  const {wallet} = useWallet();

  // We should always have an available keypair here.
  if (!wallet) {
    throw new Error('Wallet is null or undefined');
  }

  return (
    <View>
      <BottomsheetHeader title={'Sign and Send Transactions'} />
      <Divider style={styles.spacer} />
      <View style={styles.buttonGroup}>
        <Button
          style={styles.actionButton}
          onPress={() => {
            signAndSendTransactions(wallet, request);
          }}
          mode="contained">
          Sign and Send
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
  header: {
    textAlign: 'center',
    color: 'black',
    fontSize: 32,
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
