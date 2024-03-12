import {Keypair} from '@solana/web3.js';
import React, { useEffect, useState } from 'react';
import {NativeModules, StyleSheet, View} from 'react-native';
import {Button, Text} from 'react-native-paper';
import {
  MWARequestFailReason,
  resolve,
  SignAndSendTransactionsRequest,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import {SolanaSigningUseCase} from '../utils/SolanaSigningUseCase';
import {
  SendTransactionsUseCase,
  SendTransactionsError,
} from '../utils/SendTransactionsUseCase';
import {useWallet} from '../components/WalletProvider';
import MWABottomsheetHeader from '../components/MWABottomsheetHeader';
import { useClientTrust } from '../components/ClientTrustProvider';

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
    resolve(request, {
      failReason: MWARequestFailReason.InvalidSignatures,
      valid: valid,
    });
    return;
  }
  try {
    const signatures = await SendTransactionsUseCase.sendSignedTransactions(
      signedTransactions,
      request.minContextSlot ? request.minContextSlot : undefined,
    );
    resolve(request, {
      signedTransactions: signatures,
    });
  } catch (error) {
    console.log(`Error during signAndSendTransactions: ${error}`);
    if (error instanceof SendTransactionsError) {
      resolve(request, {
        failReason: MWARequestFailReason.InvalidSignatures,
        valid: error.valid,
      });
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
  const {clientTrustUseCase} = useClientTrust();
  const [verified, setVerified] = useState(false);

  // We should always have an available keypair here.
  if (!wallet) {
    throw new Error('Wallet is null or undefined');
  }

  useEffect(() => {

    const verifyClient = async () => {
      const authScope = new TextDecoder().decode(request.authorizationScope);
      const verified = await clientTrustUseCase?.verifyPrivaledgedMethodSource(
        authScope, 
        request.appIdentity?.identityUri
      ) ?? false;
      setVerified(verified);

      // Note: this will silently decline the request. Not great UX
      // The wallet should inform the user that the source of this request was not verified  
      if (!verified) {
        resolve(request, {failReason: MWARequestFailReason.UserDeclined});
      }
    }

    verifyClient();
    
  }, []);

  return (
    <View>
      <MWABottomsheetHeader
        title={'Sign and Send Transactions'}
        cluster={request.chain}
        appIdentity={request.appIdentity}>
        <Text style={styles.content}>
          This request has {request.payloads.length}{' '}
          {request.payloads.length > 1 ? 'payloads' : 'payload'} to sign.
        </Text>
      </MWABottomsheetHeader>
      <View style={styles.buttonGroup}>
        <Button
          style={styles.actionButton}
          disabled={!verified}
          onPress={() => {
            signAndSendTransactions(wallet, request);
          }}
          mode="contained">
          Sign and Send
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
    color: 'green',
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
