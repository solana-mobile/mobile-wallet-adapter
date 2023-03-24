import {Keypair} from '@solana/web3.js';
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

const useWallet = () => {
  const [keypair, setKeypair] = useState<Keypair | null>(null);

  useEffect(() => {
    const generateKeypair = async () => {
      const keypair = await Keypair.generate();
      setKeypair(keypair);
    };

    generateKeypair();
  }, []);

  return {wallet: keypair};
};

const useMWAEvent = () => {
  const [event, setEvent] = useState<any | null>(null);

  useEffect(() => {
    const eventEmitter = new NativeEventEmitter(NativeModules.MwaWalletLibModule);
    eventEmitter.addListener("MobileWalletAdapterServiceEvent", (event) => {
      NativeModules.WalletLib.log("MWA Event: " + event.type);
      setEvent(event);
    });
  }, []);

  return {event};
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
  const {wallet} = useWallet();
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
        return <SignPayloadsScreen wallet={wallet} event={event} />;
      case "AUTHORIZE_DAPP":
        return <AuthenticationScreen publicKey={wallet.publicKey} />;
      default:
        console.log("loading screen")
        return <LoadingScreen />;
    }

    return <LoadingScreen />;
  }

  return (
    <View>
      {/* TODO: should put the intent url somewhere else */
        intentUrl && intentUrl.startsWith("solana-wallet:/v1/associate/local") ?
          getComponent(event) : <MainScreen />
      }
    </View>
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
