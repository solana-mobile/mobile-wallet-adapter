import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {StyleSheet, View, BackHandler, ActivityIndicator} from 'react-native';
import Modal from 'react-native-modal';
import {
  MobileWalletAdapterServiceEventType,
  MobileWalletAdapterConfig,
  useMobileWalletAdapterRequest,
  SessionTerminatedRequest,
  MobileWalletAdapterServiceRequest,
  AuthorizeDappRequest,
  SignPayloadsRequest,
  MobileWalletAdapterServiceRemoteRequest,
  SignAndSendTransactionsRequest,
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
    case MobileWalletAdapterServiceEventType.SignAndSendTransactions:
      return (
        <SignAndSendTransactionsScreen
          request={request as SignAndSendTransactionsRequest}
        />
      );
    case MobileWalletAdapterServiceEventType.SignTransactions:
    case MobileWalletAdapterServiceEventType.SignMessages:
      return <SignPayloadsScreen request={request as SignPayloadsRequest} />;
    case MobileWalletAdapterServiceEventType.AuthorizeDapp:
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
  const {request} = useMobileWalletAdapterRequest('Example RN Wallet', config);

  const endWalletSession = useCallback(() => {
    setTimeout(() => {
      console.log('Exit App');
      setIsVisible(false);
      if (
        request !== null &&
        request !== undefined &&
        request instanceof MobileWalletAdapterServiceRemoteRequest
      ) {
        (
          request as MobileWalletAdapterServiceRemoteRequest
        ).completeWithDecline();
      }
      BackHandler.exitApp();
    }, 200);
  }, [request]);

  useEffect(() => {
    if (!request) {
      return;
    }

    if (request instanceof SessionTerminatedRequest) {
      endWalletSession();
    }
  }, [request, endWalletSession]);

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
