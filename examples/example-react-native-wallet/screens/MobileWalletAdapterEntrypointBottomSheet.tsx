import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {StyleSheet, View, BackHandler, ActivityIndicator} from 'react-native';
import Modal from 'react-native-modal';
import {
  MobileWalletAdapterServiceRequestEventType,
  MobileWalletAdapterConfig,
  MobileWalletAdapterServiceRequest,
  AuthorizeDappRequest,
  SignPayloadsRequest,
  SignAndSendTransactionsRequest,
  useMobileWalletAdapterSession,
  SessionTerminatedEvent,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import AuthenticationScreen from '../bottomsheets/AuthenticationScreen';
import LoadingScreen from '../bottomsheets/LoadingScreen';
import SignAndSendTransactionsScreen from '../bottomsheets/SignAndSendTransactionsScreen';
import SignPayloadsScreen from '../bottomsheets/SignPayloadsScreen';
import WalletProvider from '../components/WalletProvider';

function getRequestScreenComponent(
  request: MobileWalletAdapterServiceRequest | null | undefined,
) {
  switch (request?.type) {
    case MobileWalletAdapterServiceRequestEventType.SignAndSendTransactions:
      return (
        <SignAndSendTransactionsScreen
          request={request as SignAndSendTransactionsRequest}
        />
      );
    case MobileWalletAdapterServiceRequestEventType.SignTransactions:
    case MobileWalletAdapterServiceRequestEventType.SignMessages:
      return <SignPayloadsScreen request={request as SignPayloadsRequest} />;
    case MobileWalletAdapterServiceRequestEventType.AuthorizeDapp:
      return <AuthenticationScreen request={request as AuthorizeDappRequest} />;
    default:
      return <ActivityIndicator size="large" />;
  }
}

export default function MobileWalletAdapterEntrypointBottomSheet() {
  const [isVisible, setIsVisible] = useState(true);
  const config: MobileWalletAdapterConfig = useMemo(() => {
    return {
      supportsSignAndSendTransactions: true,
      maxTransactionsPerSigningRequest: 10,
      maxMessagesPerSigningRequest: 10,
      supportedTransactionVersions: [0, 'legacy'],
      noConnectionWarningTimeoutMs: 3000,
    };
  }, []);
  const {request, sessionEvent} = useMobileWalletAdapterSession(
    'Example RN Wallet',
    config,
  );

  const endWalletSession = useCallback(() => {
    setTimeout(() => {
      console.log('Exit App');
      setIsVisible(false);
      if (
        request !== null &&
        request !== undefined &&
        request instanceof MobileWalletAdapterServiceRequest
      ) {
        // If we have a request, respond to the dApp with a decline and completes the request.
        (request as MobileWalletAdapterServiceRequest).completeWithDecline();
      }
      BackHandler.exitApp();
    }, 200);
  }, [request]);

  // Listen for session termination event to close our wallet app and navigate back to dapp.
  useEffect(() => {
    if (!sessionEvent) {
      return;
    }

    if (sessionEvent instanceof SessionTerminatedEvent) {
      endWalletSession();
    }
  }, [sessionEvent, endWalletSession]);

  return (
    <Modal
      style={styles.container}
      isVisible={isVisible}
      swipeDirection={['up', 'down']}
      onSwipeComplete={() => endWalletSession()}
      onBackdropPress={() => endWalletSession()}>
      <WalletProvider>
        <View style={styles.bottomSheet}>
          {getRequestScreenComponent(request)}
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
});
