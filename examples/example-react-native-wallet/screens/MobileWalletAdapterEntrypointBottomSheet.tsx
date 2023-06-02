import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {StyleSheet, View, BackHandler, ActivityIndicator} from 'react-native';
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
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import AuthenticationScreen from '../bottomsheets/AuthenticationScreen';
import SignAndSendTransactionsScreen from '../bottomsheets/SignAndSendTransactionsScreen';
import SignPayloadsScreen from '../bottomsheets/SignPayloadsScreen';
import WalletProvider from '../components/WalletProvider';

type SignPayloadsRequest = SignTransactionsRequest | SignMessagesRequest;

function getRequestScreenComponent(request: MWARequest | null | undefined) {
  switch (request?.__type) {
    case MWARequestType.SignAndSendTransactionsRequest:
      return (
        <SignAndSendTransactionsScreen
          request={request as SignAndSendTransactionsRequest}
        />
      );
    case MWARequestType.SignTransactionsRequest:
    case MWARequestType.SignMessagesRequest:
      return <SignPayloadsScreen request={request as SignPayloadsRequest} />;
    case MWARequestType.AuthorizeDappRequest:
      return <AuthenticationScreen request={request as AuthorizeDappRequest} />;
    default:
      return <ActivityIndicator size="large" />;
  }
}

export default function MobileWalletAdapterEntrypointBottomSheet() {
  const [isVisible, setIsVisible] = useState(true);
  const [curRequest, setCurRequest] = useState<MWARequest | undefined>(
    undefined,
  );
  const [curEvent, setCurEvent] = useState<MWASessionEvent | undefined>(
    undefined,
  );

  const endWalletSession = useCallback(() => {
    setTimeout(() => {
      console.log('Exit App');
      setIsVisible(false);
      BackHandler.exitApp();
    }, 200);
  }, []);

  const config: MobileWalletAdapterConfig = useMemo(() => {
    return {
      supportsSignAndSendTransactions: true,
      maxTransactionsPerSigningRequest: 10,
      maxMessagesPerSigningRequest: 10,
      supportedTransactionVersions: [0, 'legacy'],
      noConnectionWarningTimeoutMs: 3000,
    };
  }, []);

  // MWA Session Handlers
  const handleRequest = useCallback((request: MWARequest) => {
    setCurRequest(request);
  }, []);
  const handleSessionEvent = useCallback((sessionEvent: MWASessionEvent) => {
    setCurEvent(sessionEvent);
  }, []);

  useEffect(() => {
    if (!curEvent) {
      return;
    }

    if (curEvent.__type === MWASessionEventType.SessionTerminatedEvent) {
      endWalletSession();
    }
  }, [curEvent, endWalletSession]);

  // Start an MWA session
  useMobileWalletAdapterSession(
    'Example RN Wallet',
    config,
    handleRequest,
    handleSessionEvent,
  );

  return (
    <Modal
      style={styles.container}
      isVisible={isVisible}
      swipeDirection={['up', 'down']}
      onSwipeComplete={() => endWalletSession()}
      onBackdropPress={() => endWalletSession()}>
      <WalletProvider>
        <View style={styles.bottomSheet}>
          {getRequestScreenComponent(curRequest)}
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
