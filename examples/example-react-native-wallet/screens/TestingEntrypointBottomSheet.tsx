import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  StyleSheet,
  View,
  Text,
  BackHandler,
  ActivityIndicator,
  TouchableOpacity,
} from 'react-native';
import Modal from 'react-native-modal';
import {
  MobileWalletAdapterConfig,
  AuthorizeDappRequest,
  SignMessagesRequest,
  SignTransactionsRequest,
  SignAndSendTransactionsRequest,
  useMobileWalletAdapterSession,
  MWARequestType,
  MWARequest,
  MWASessionEvent,
  MWASessionEventType,
  resolve,
  MWAResponse,
  AuthorizeDappResponse,
  MWARequestFailReason,
  SignAndSendTransactionsResponse,
  SignTransactionsResponse,
  SignMessagesResponse,
  UserDeclinedResponse,
  AuthorizationNotValid,
  InvalidSignaturesResponse,
  TooManyPayloadsResponse,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import WalletProvider from '../components/WalletProvider';
import {useWallet} from '@solana/wallet-adapter-react/lib/types/useWallet';
import {Keypair} from '@solana/web3.js';

interface SendResponseButtonProps {
  title: string;
  request: MWARequest;
  response: MWAResponse;
}

const SendResponseButton = ({
  title,
  request,
  response,
}: SendResponseButtonProps) => {
  let typedRequest;
  let typedResponse;
  switch (request.__type) {
    case MWARequestType.AuthorizeDappRequest:
      typedRequest = request as AuthorizeDappRequest;
      typedResponse = response as AuthorizeDappResponse;
      break;
    case MWARequestType.SignMessagesRequest:
      typedRequest = request as SignMessagesRequest;
      typedResponse = response as SignMessagesResponse;
      break;
    case MWARequestType.SignTransactionsRequest:
      typedRequest = request as SignTransactionsRequest;
      typedResponse = response as SignTransactionsResponse;
      break;
    case MWARequestType.SignAndSendTransactionsRequest:
      typedRequest = request as SignAndSendTransactionsRequest;
      typedResponse = response as SignAndSendTransactionsResponse;
      break;
    default:
      console.warn('Unsupported request type');
      return null;
  }

  return (
    <TouchableOpacity
      style={styles.button}
      onPress={async () => {
        await resolve(typedRequest, typedResponse);
      }}>
      <Text style={styles.buttonText}>{title}</Text>
    </TouchableOpacity>
  );
};

export default function TestingEntrypointBottomSheet() {
  const [isVisible, setIsVisible] = useState(true);
  const endWalletSession = useCallback(() => {
    setTimeout(() => {
      console.log('Exit App');
      setIsVisible(false);
      BackHandler.exitApp();
    }, 200);
  }, []);

  const genericRequest = {
    requestId: 'reqid',
    sessionId: 'sessionid',
    cluster: 'devnet',
    authorizationScope: new Uint8Array([1, 2, 3, 4]),
  };

  const authorizeDappResponse = {
    publicKey: Keypair.generate().publicKey.toBytes(),
    label: 'Wallet Label',
  } as AuthorizeDappResponse;

  const signPayloadsResponse = {
    signedPayloads: [
      new Uint8Array([1, 2, 3]),
      new Uint8Array([1, 2, 3, 4, 5]),
    ],
  };

  const signAndSendTransactionsResponse = {
    signedTransactions: [
      new Uint8Array([1, 2, 3]),
      new Uint8Array([1, 2, 3, 4, 5]),
    ],
  };

  const userDeclinedResponse = {
    failReason: MWARequestFailReason.UserDeclined,
  } as UserDeclinedResponse;

  const authorizationNotValidResponse = {
    failReason: MWARequestFailReason.AuthorizationNotValid,
  } as AuthorizationNotValidResponse;

  const invalidSignaturesResponse = {
    failReason: MWARequestFailReason.InvalidSignatures,
    valid: [false, true],
  } as InvalidSignaturesResponse;

  const tooManyPayloadsResponse = {
    failReason: MWARequestFailReason.TooManyPayloads,
  } as TooManyPayloadsResponse;

  return (
    <Modal
      style={styles.container}
      isVisible={isVisible}
      swipeDirection={['up', 'down']}
      onSwipeComplete={() => endWalletSession()}
      onBackdropPress={() => endWalletSession()}>
      <WalletProvider>
        <View style={styles.bottomSheet}>
          <SendResponseButton
            title="Authorize Dapp"
            request={{
              ...genericRequest,
              __type: MWARequestType.AuthorizeDappRequest,
            }}
            response={authorizeDappResponse}
          />
          <SendResponseButton
            title="Sign Transaction"
            request={{
              ...genericRequest,
              __type: MWARequestType.SignTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            response={signPayloadsResponse}
          />
          <SendResponseButton
            title="Sign Transaction"
            request={{
              ...genericRequest,
              __type: MWARequestType.SignMessagesRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            response={signPayloadsResponse}
          />
          <SendResponseButton
            title="Sign And Send Transaction"
            request={{
              ...genericRequest,
              __type: MWARequestType.SignAndSendTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            response={signAndSendTransactionsResponse}
          />

          <Text> Fail Responses </Text>

          <SendResponseButton
            title="User Declined Sign And Send"
            request={{
              ...genericRequest,
              __type: MWARequestType.SignAndSendTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            response={userDeclinedResponse}
          />

          <SendResponseButton
            title="Too many payloads"
            request={{
              ...genericRequest,
              __type: MWARequestType.SignAndSendTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            response={tooManyPayloadsResponse}
          />

          <SendResponseButton
            title="Invalid Signatures"
            request={{
              ...genericRequest,
              __type: MWARequestType.SignAndSendTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            response={invalidSignaturesResponse}
          />

          <SendResponseButton
            title="Auth not valid"
            request={{
              ...genericRequest,
              __type: MWARequestType.SignAndSendTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            response={authorizationNotValidResponse}
          />
        </View>
      </WalletProvider>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: {
    justifyContent: 'flex-end',
    margin: 0,
  },
  bottomSheet: {
    backgroundColor: 'white',
    padding: 16,
    borderTopLeftRadius: 8,
    borderTopRightRadius: 8,
  },
  button: {
    backgroundColor: 'blue',
    padding: 10,
    borderRadius: 5,
    marginVertical: 10,
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
    textAlign: 'center',
  },
});
