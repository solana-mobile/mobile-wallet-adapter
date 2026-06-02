import React, {useCallback, useState} from 'react';
import {
  BackHandler,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import Modal from 'react-native-modal';
import {
  AuthorizeDappRequest,
  SignMessagesRequest,
  SignTransactionsRequest,
  SignAndSendTransactionsRequest,
  MWARequestType,
  resolve,
  AuthorizeDappResponse,
  MWARequestFailReason,
  SignAndSendTransactionsResponse,
  SignTransactionsResponse,
  SignMessagesResponse,
  UserDeclinedResponse,
  AuthorizationNotValidResponse,
  InvalidSignaturesResponse,
  TooManyPayloadsResponse,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import WalletProvider from '../components/WalletProvider';
import {Keypair} from '@solana/web3.js';

interface SendResponseButtonProps {
  title: string;
}

type SendResponseButtonTypedProps =
  | Readonly<{
      requestType: MWARequestType.AuthorizeDappRequest;
      request: AuthorizeDappRequest;
      response: AuthorizeDappResponse;
    }>
  | Readonly<{
      requestType: MWARequestType.SignAndSendTransactionsRequest;
      request: SignAndSendTransactionsRequest;
      response: SignAndSendTransactionsResponse;
    }>
  | Readonly<{
      requestType: MWARequestType.SignMessagesRequest;
      request: SignMessagesRequest;
      response: SignMessagesResponse;
    }>
  | Readonly<{
      requestType: MWARequestType.SignTransactionsRequest;
      request: SignTransactionsRequest;
      response: SignTransactionsResponse;
    }>;

const SendResponseButton = (
  props: SendResponseButtonProps & SendResponseButtonTypedProps,
) => {
  const {title} = props;
  return (
    <TouchableOpacity
      style={styles.button}
      onPress={async () => {
        switch (props.requestType) {
          case MWARequestType.AuthorizeDappRequest:
            await resolve(props.request, props.response);
            break;
          case MWARequestType.SignAndSendTransactionsRequest:
            await resolve(props.request, props.response);
            break;
          case MWARequestType.SignMessagesRequest:
            await resolve(props.request, props.response);
            break;
          case MWARequestType.SignTransactionsRequest:
            await resolve(props.request, props.response);
            break;
        }
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
    authorizationScope: new Uint8Array([1, 2, 3, 4]),
    chain: 'solana:devnet',
    cluster: 'devnet',
    requestId: 'reqid',
    sessionId: 'sessionid',
  };

  const authorizeDappResponse: AuthorizeDappResponse = {
    accounts: [
      {
        accountLabel: 'Wallet Label',
        chains: ['solana:devnet'],
        publicKey: Keypair.generate().publicKey.toBytes(),
      },
    ],
  };

  const signPayloadsResponse: SignMessagesResponse = {
    signedPayloads: [
      new Uint8Array([1, 2, 3]),
      new Uint8Array([1, 2, 3, 4, 5]),
    ],
  };

  const signAndSendTransactionsResponse: SignAndSendTransactionsResponse = {
    signedTransactions: [
      new Uint8Array([1, 2, 3]),
      new Uint8Array([1, 2, 3, 4, 5]),
    ],
  };

  const userDeclinedResponse: UserDeclinedResponse = {
    failReason: MWARequestFailReason.UserDeclined,
  };

  const authorizationNotValidResponse: AuthorizationNotValidResponse = {
    failReason: MWARequestFailReason.AuthorizationNotValid,
  };

  const invalidSignaturesResponse: InvalidSignaturesResponse = {
    failReason: MWARequestFailReason.InvalidSignatures,
    valid: [false, true],
  };

  const tooManyPayloadsResponse: TooManyPayloadsResponse = {
    failReason: MWARequestFailReason.TooManyPayloads,
  };

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
            request={{
              ...genericRequest,
              __type: MWARequestType.AuthorizeDappRequest,
            }}
            requestType={MWARequestType.AuthorizeDappRequest}
            response={authorizeDappResponse}
            title="Authorize Dapp"
          />
          <SendResponseButton
            request={{
              ...genericRequest,
              __type: MWARequestType.SignTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            requestType={MWARequestType.SignTransactionsRequest}
            response={signPayloadsResponse}
            title="Sign Transaction"
          />
          <SendResponseButton
            request={{
              ...genericRequest,
              __type: MWARequestType.SignMessagesRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            requestType={MWARequestType.SignMessagesRequest}
            response={signPayloadsResponse}
            title="Sign Transaction"
          />
          <SendResponseButton
            request={{
              ...genericRequest,
              __type: MWARequestType.SignAndSendTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            requestType={MWARequestType.SignAndSendTransactionsRequest}
            response={signAndSendTransactionsResponse}
            title="Sign And Send Transaction"
          />

          <Text> Fail Responses </Text>

          <SendResponseButton
            request={{
              ...genericRequest,
              __type: MWARequestType.SignAndSendTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            requestType={MWARequestType.SignAndSendTransactionsRequest}
            response={userDeclinedResponse}
            title="User Declined Sign And Send"
          />

          <SendResponseButton
            request={{
              ...genericRequest,
              __type: MWARequestType.SignAndSendTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            requestType={MWARequestType.SignAndSendTransactionsRequest}
            response={tooManyPayloadsResponse}
            title="Too many payloads"
          />

          <SendResponseButton
            request={{
              ...genericRequest,
              __type: MWARequestType.SignAndSendTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            requestType={MWARequestType.SignAndSendTransactionsRequest}
            response={invalidSignaturesResponse}
            title="Invalid Signatures"
          />

          <SendResponseButton
            request={{
              ...genericRequest,
              __type: MWARequestType.SignAndSendTransactionsRequest,
              payloads: [
                new Uint8Array([1, 2, 3]),
                new Uint8Array([1, 2, 3, 4, 5]),
              ],
            }}
            requestType={MWARequestType.SignAndSendTransactionsRequest}
            response={authorizationNotValidResponse}
            title="Auth not valid"
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
