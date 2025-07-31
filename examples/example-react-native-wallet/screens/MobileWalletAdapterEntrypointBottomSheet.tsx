import 'fast-text-encoding';
import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  StyleSheet,
  View,
  BackHandler,
  ActivityIndicator,
  Linking,
} from 'react-native';
import Modal from 'react-native-modal';
import {
  MobileWalletAdapterConfig,
  AuthorizeDappRequest,
  SignMessagesRequest,
  SignTransactionsRequest,
  SignAndSendTransactionsRequest,
  MWARequestType,
  MWARequest,
  MWASessionEvent,
  MWASessionEventType,
  resolve,
  ReauthorizeDappCompleteResponse,
  DeauthorizeDappResponse,
  MWARequestFailReason,
  getCallingPackage,
  initializeMWAEventListener,
  initializeMobileWalletAdapterSession,
  SolanaMWAWalletLibErrorCode,
  SolanaMWAWalletLibError,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

import AuthenticationScreen from '../bottomsheets/AuthenticationScreen';
import SignAndSendTransactionsScreen from '../bottomsheets/SignAndSendTransactionsScreen';
import SignPayloadsScreen from '../bottomsheets/SignPayloadsScreen';
import WalletProvider, {useWallet} from '../components/WalletProvider';
import ClientTrustProvider from '../components/ClientTrustProvider';
import {
  ClientTrustUseCase,
  NotVerifiable,
  VerificationFailed,
  VerificationSucceeded,
} from '../utils/ClientTrustUseCase';
import SignInScreen from '../bottomsheets/SignInScreen';

const SolanaSignTransactions = 'solana:signTransactions'
const SolanaSignInWithSolana = 'solana:signInWithSolana'

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
      return request.signInPayload ? (
        <SignInScreen request={request as AuthorizeDappRequest} />
      ) : (
        <AuthenticationScreen request={request as AuthorizeDappRequest} />
      );
    default:
      return <ActivityIndicator size="large" />;
  }
}

export default function MobileWalletAdapterEntrypointBottomSheet() {
  const [isVisible, setIsVisible] = useState(true);
  const {wallet} = useWallet();
  const [clientTrustUseCase, setClientTrustUseCase] =
    useState<ClientTrustUseCase | null>(null);
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
      maxTransactionsPerSigningRequest: 10,
      maxMessagesPerSigningRequest: 10,
      supportedTransactionVersions: [0, 'legacy'],
      noConnectionWarningTimeoutMs: 3000,
      optionalFeatures: [SolanaSignTransactions, SolanaSignInWithSolana],
    };
  }, []);

  // MWA Session Handlers
  const handleRequest = useCallback((request: MWARequest) => {
    setCurRequest(request);
  }, []);
  const handleSessionEvent = useCallback((sessionEvent: MWASessionEvent) => {
    setCurEvent(sessionEvent);
  }, []);

  // Initialize the MWA by:
  //  1. Begin listening for MWA events
  //  2. Starting the MWA session
  useEffect(() => {
    async function initializeMWASession() {
      try {
        const sessionId = await initializeMobileWalletAdapterSession(
          'wallet label',
          config,
        );
        console.log('sessionId: ' + sessionId);
      } catch (e: any) {
        if (e instanceof SolanaMWAWalletLibError) {
          console.error(e.name, e.code, e.message);
        } else {
          console.error(e);
        }
      }
    }
    const listener = initializeMWAEventListener(
      handleRequest,
      handleSessionEvent,
    );
    initializeMWASession();
    return () => listener.remove();
  }, [config, handleRequest, handleSessionEvent]);

  useEffect(() => {
    const initClientTrustUseCase = async () => {
      let callingPackage: string | undefined = await getCallingPackage();
      let clientTrustUseCase = new ClientTrustUseCase(
        (await Linking.getInitialURL()) ?? '',
        callingPackage,
      );
      setClientTrustUseCase(clientTrustUseCase);
    };

    initClientTrustUseCase();
  }, []);

  // Listen for termination event and exit app
  useEffect(() => {
    if (!curEvent) {
      return;
    }

    if (curEvent.__type === MWASessionEventType.SessionTerminatedEvent) {
      endWalletSession();
    }
  }, [curEvent, endWalletSession]);

  useEffect(() => {
    if (!curRequest) {
      return;
    }

    // handling reauth here, could probably be cleaner
    // important thing here is that we verify the source and complete the request without bugging the user
    if (curRequest.__type === MWARequestType.ReauthorizeDappRequest) {
      let request = curRequest;
      const authScope = new TextDecoder().decode(request.authorizationScope);

      // try to verify the reauthorization source, with 3 second timeout
      Promise.race([
        clientTrustUseCase!!.verifyReauthorizationSource(
          authScope,
          request.appIdentity?.identityUri,
        ),
        async () => {
          setTimeout(() => {
            throw new Error(
              'Timed out waiting for reauthorization source verification',
            );
          }, 3000);
        },
      ])
        .then(verificationState => {
          if (verificationState instanceof VerificationSucceeded) {
            console.log('Reauthorization source verification succeeded');
            resolve(request, {
              authorizationScope: new TextEncoder().encode(
                verificationState?.authorizationScope,
              ),
            } as ReauthorizeDappCompleteResponse);
          } else if (verificationState instanceof NotVerifiable) {
            console.log('Reauthorization source not verifiable; approving');
            resolve(request, {
              authorizationScope: new TextEncoder().encode(
                verificationState?.authorizationScope,
              ),
            } as ReauthorizeDappCompleteResponse);
          } else if (verificationState instanceof VerificationFailed) {
            console.log('Reauthorization source verification failed');
            resolve(request, {
              failReason: MWARequestFailReason.AuthorizationNotValid,
            });
          }
        })
        .catch(() => {
          console.log(
            'Timed out waiting for reauthorization source verification',
          );
          resolve(request, {
            failReason: MWARequestFailReason.AuthorizationNotValid,
          });
        });
    }

    if (curRequest.__type === MWARequestType.DeauthorizeDappRequest) {
      resolve(curRequest, {} as DeauthorizeDappResponse);
    }
  }, [wallet, curRequest, endWalletSession, clientTrustUseCase]);

  return (
    <Modal
      style={styles.container}
      isVisible={isVisible}
      swipeDirection={['up', 'down']}
      onSwipeComplete={() => endWalletSession()}
      onBackdropPress={() => endWalletSession()}>
      <WalletProvider>
        <ClientTrustProvider clientTrustUseCase={clientTrustUseCase}>
          <View style={styles.bottomSheet}>
            {getRequestScreenComponent(curRequest)}
          </View>
        </ClientTrustProvider>
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
