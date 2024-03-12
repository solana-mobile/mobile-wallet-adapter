import {Keypair} from '@solana/web3.js';
import React, { useEffect, useState } from 'react';
import {StyleSheet, View} from 'react-native';
import {Button, Text} from 'react-native-paper';

import {
  MWARequestFailReason,
  MWARequestType,
  resolve,
  SignMessagesRequest,
  SignTransactionsRequest,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import {SolanaSigningUseCase} from '../utils/SolanaSigningUseCase';
import {useWallet} from '../components/WalletProvider';
import MWABottomsheetHeader from '../components/MWABottomsheetHeader';
import { useClientTrust } from '../components/ClientTrustProvider';

type SignPayloadsRequest = SignTransactionsRequest | SignMessagesRequest;

const signPayloads = async (wallet: Keypair, request: SignPayloadsRequest) => {
  if (request.__type === MWARequestType.SignTransactionsRequest) {
    signTransactions(wallet, request);
  } else if (request.__type === MWARequestType.SignMessagesRequest) {
    signMessages(wallet, request);
  } else {
    console.warn('Invalid payload screen request type');
    return;
  }
};

const signTransactions = async (
  wallet: Keypair,
  request: SignTransactionsRequest,
) => {
  const valid: boolean[] = request.payloads.map(_ => {
    return true;
  });

  let signedPayloads: Uint8Array[] = request.payloads.map((numArray, index) => {
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

  // If all valid, then call complete request
  if (!valid.includes(false)) {
    resolve(request, {signedPayloads});
  } else {
    resolve(request, {
      failReason: MWARequestFailReason.InvalidSignatures,
      valid: valid,
    });
  }
};

const signMessages = async (wallet: Keypair, request: SignMessagesRequest) => {
  const valid: boolean[] = request.payloads.map(_ => {
    return true;
  });

  const signedPayloads = request.payloads.map(numArray => {
    return SolanaSigningUseCase.signMessage(new Uint8Array(numArray), wallet);
  });

  // If all valid, then call complete request
  if (!valid.includes(false)) {
    resolve(request, {signedPayloads});
  } else {
    resolve(request, {
      failReason: MWARequestFailReason.InvalidSignatures,
      valid: valid,
    });
  }
};

interface SignPayloadsScreenProps {
  request: SignPayloadsRequest;
}

// this view is basically the same as AuthenticationScreen.
// Should either combine them or pull common code to base abstraction
export default function SignPayloadsScreen({request}: SignPayloadsScreenProps) {
  const {wallet} = useWallet();
  const {clientTrustUseCase} = useClientTrust();
  const [verified, setVerified] = useState(false);
  const isSignTransactions =
    request.__type === MWARequestType.SignTransactionsRequest;

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
        if (request.__type == MWARequestType.SignTransactionsRequest) {
          resolve(request, {failReason: MWARequestFailReason.UserDeclined});
        } else if (request.__type == MWARequestType.SignMessagesRequest) {
          resolve(request, {failReason: MWARequestFailReason.UserDeclined});
        }
      }
    }

    verifyClient();
    
  }, []);

  return (
    <View>
      <MWABottomsheetHeader
        title={'Sign ' + (isSignTransactions ? 'transactions' : 'messages')}
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
            signPayloads(wallet, request);
          }}
          mode="contained">
          Sign
        </Button>
        <Button 
          style={styles.actionButton} 
          onPress={() => {
            if (request.__type == MWARequestType.SignTransactionsRequest) {
              resolve(request, {failReason: MWARequestFailReason.UserDeclined});
            } else if (request.__type == MWARequestType.SignMessagesRequest) {
              resolve(request, {failReason: MWARequestFailReason.UserDeclined});
            }
          }}
          mode="outlined">
          Reject
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
  content: {
    textAlign: 'left',
    color: 'green',
    fontSize: 18,
  },
});
