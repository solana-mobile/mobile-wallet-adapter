import "fast-text-encoding";
import React, { useEffect, useState } from 'react';
import {StyleSheet, View} from 'react-native';
import {Button, Divider, Text} from 'react-native-paper';
import {
  AuthorizeDappCompleteResponse,
  AuthorizeDappRequest,
  MWARequestFailReason,
  resolve,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import {useWallet} from '../components/WalletProvider';
import MWABottomsheetHeader from '../components/MWABottomsheetHeader';
import { VerificationInProgress, VerificationState } from '../utils/ClientTrustUseCase';
import { useClientTrust } from "../components/ClientTrustProvider";
import { SolanaSignInInputWithRequiredFields, createSignInMessageText } from "@solana/wallet-standard-util";
import { SolanaSigningUseCase } from "../utils/SolanaSigningUseCase";
import { Base64 } from "js-base64";

interface SignInScreenProps {
  request: AuthorizeDappRequest;
}

export default function SignInScreen({
  request,
}: SignInScreenProps) {
  const {wallet} = useWallet();
  const {clientTrustUseCase} = useClientTrust();
  const [verificationState, setVerificationState] = useState<VerificationState | undefined>(undefined);

  // We should always have an available keypair here.
  if (!wallet) {
    throw new Error('Wallet is null or undefined');
  }

  const signInMessage = createSignInMessageText({ 
    ...request.signInPayload as SolanaSignInInputWithRequiredFields,
    address: wallet?.publicKey.toBase58()
  })

  useEffect(() => {

    const verifyClient = async () => {
      const verificationState = await clientTrustUseCase?.verifyAuthorizationSource(request.appIdentity?.identityUri);
      setVerificationState(verificationState);
    }

    verifyClient();
    
  }, []);

  return (
    <View>
      <MWABottomsheetHeader
        title={'Sign In'}
        cluster={request.chain}
        appIdentity={request.appIdentity}
        verificationState={verificationState ?? new VerificationInProgress('')}
      />
      <View style={styles.signInSection}>
        <Text style={styles.signInHeader}>Message:</Text>
        <Text style={styles.signInText}>{signInMessage}</Text>
      </View>
      <Divider style={styles.spacer} />
      <View style={styles.buttonGroup}>
        <Button
          style={styles.actionButton}
          onPress={() => {
            const messageBytes = new TextEncoder().encode(signInMessage)
            const signature = SolanaSigningUseCase.signMessage(messageBytes, wallet);
            resolve(request, {
              accounts: [{
                publicKey: wallet.publicKey.toBytes(),
                accountLabel: 'Backpack',
                icon: 'data:text/plain;base64',
                chains: ['solana:devnet', 'solana:testnet'],
                features: ['solana:signTransactions']
              }],
              authorizationScope: new TextEncoder().encode(verificationState?.authorizationScope),
              signInResult: {
                address: Base64.fromUint8Array(wallet.publicKey.toBytes()),
                signed_message: Base64.fromUint8Array(messageBytes),
                signature: Base64.fromUint8Array(signature)
              }
            } as AuthorizeDappCompleteResponse);
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
  signInSection: {
    display: 'flex',
    flexDirection: 'column',
    backgroundColor: '#d3d3d3', // light gray background
    borderRadius: 8, // rounded corners
    padding: 10, // tight padding
    marginVertical: 10, // some vertical margin
  },
  signInText: {
    textAlign: 'left',
    color: 'black',
    fontSize: 16,
  },
  signInHeader: {
    textAlign: 'left',
    color: 'black',
    fontWeight: 'bold',
    fontSize: 18,
  },
  spacer: {
    marginVertical: 16,
    width: '100%',
    height: 1,
  },
});
