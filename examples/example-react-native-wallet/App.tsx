import {Keypair, Signer} from '@solana/web3.js';
import React, {useState, useEffect} from 'react';
import {
  BackHandler,
  Linking,
  NativeEventEmitter,
  NativeModules,
  StyleSheet,
  View,
} from 'react-native';

import MainScreen from './screens/MainScreen';
import LoadingScreen from './screens/LoadingScreen';
import AuthenticationScreen from './screens/AuthenticationScreen';
import SignPayloadsScreen from './screens/SignPayloadsScreen';
import WalletProvider from './utils/WalletContext';

function initMWA(url: string) {
  NativeModules.WalletLib.createScenario("ExampleWallet", url, (result, message) => {});
}

const useLaunchURL = () => {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    const getUrlAsync = async () => {
      // Get the intent link used to open the app
      const initialUrl = await Linking.getInitialURL();
      setUrl(initialUrl);

      // this should be dealt with elsewhere, but this is the only place where it works rn
      if (initialUrl && initialUrl.startsWith("solana-wallet:/v1/associate/local")) {
        initMWA(initialUrl);
      }
    };

    getUrlAsync();
  }, []);

  return {url};
};

export enum MobileWalletAdapterServiceEventType {
  SignTransactions = 'SIGN_TRANSACTIONS',
  SignMessages = 'SIGN_MESSAGES',
  SessionTerminated = 'SESSION_TERMINATED',
  LowPowerNoConnection = 'LOW_POWER_NO_CONNECTION',
  AuthorizeDapp = 'AUTHORIZE_DAPP',
  ReauthorizeDapp = 'REAUTHORIZE_DAPP'
};

export default function App() {
  const {url: intentUrl} = useLaunchURL();
  // const {event: walletAdapterEvent} = useMWAEvent();
  const [event, setEvent] = useState<any | null>(null);
  useEffect(() => {
    const eventEmitter = new NativeEventEmitter(NativeModules.MwaWalletLibModule);
    eventEmitter.removeAllListeners("MobileWalletAdapterServiceEvent");
    eventEmitter.addListener("MobileWalletAdapterServiceEvent", (newEvent) => {
      NativeModules.WalletLib.log("MWA Event: " + newEvent.type);
      if (!(newEvent?.type === "SESSION_TERMINATED" && event?.type === "SESSION_TERMINATED")) {
        setEvent(newEvent);
      }
    });
  }, []);

  useEffect(() => {
    // exit when MWA session ends
    // it would be better if the app went to the background rather than fully exiting, but seems 
    // this will need to be done in android. We can expose a method in the mwa module to navigate up
    if (event?.type === "SESSION_TERMINATED") { 
      setEvent(null);
      setTimeout(() => {BackHandler.exitApp();}, 200);
    }
  }, [event]);

  function getComponent(event) {
    switch(event?.type) {
      case MobileWalletAdapterServiceEventType.SignTransactions:
      case MobileWalletAdapterServiceEventType.SignMessages:
        return <SignPayloadsScreen event={event} />;
      case MobileWalletAdapterServiceEventType.AuthorizeDapp:
        return <AuthenticationScreen />;
      default:
        return <LoadingScreen />;
    }

    return <LoadingScreen />;
  }

  return (
    <WalletProvider>
      <View>
        {/* TODO: should put the intent url somewhere else */
          intentUrl && intentUrl.startsWith("solana-wallet:/v1/associate/local") ?
            getComponent(event) : <MainScreen />
        }
      </View>
    </WalletProvider>
    );
}


const styles = StyleSheet.create({
  loadingContainer: {
    height: '100%',
    justifyContent: 'center',
  },
  loadingIndicator: {
    marginVertical: 'auto',
  },
  shell: {
    height: '100%',
  },
});
