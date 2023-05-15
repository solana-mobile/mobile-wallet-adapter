import React, {createContext, useContext, useState, useEffect} from 'react';
import {Keypair} from '@solana/web3.js';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {NativeModules} from 'react-native';
import {encode, decode} from 'bs58';

interface WalletContextType {
  wallet: Keypair | null;
}

const WalletContext = createContext<WalletContextType>({wallet: null});

export const useWallet = () => {
  return useContext(WalletContext);
};

type WalletProviderProps = {
  children: React.ReactNode;
};

type EncodedKeypair = {
  publicKeyBase58: string;
  secretKeyBase58: string;
};

const ASYNC_STORAGE_KEY: string = '@reactnativefakewallet_keypair_key';

const encodeKeypair = (keypair: Keypair): EncodedKeypair => {
  return {
    publicKeyBase58: keypair.publicKey.toBase58(),
    secretKeyBase58: encode(keypair.secretKey),
  };
};

const decodeKeypair = (keypair: EncodedKeypair): Keypair => {
  var secretKey: Uint8Array = decode(keypair.secretKeyBase58);
  return Keypair.fromSecretKey(secretKey);
};

const WalletProvider: React.FC<WalletProviderProps> = ({children}) => {
  const [keypair, setKeypair] = useState<Keypair | null>(null);

  useEffect(() => {
    const generateKeypair = async () => {
      // For testing purposes, we are storing the keypair in async-storage. This is unsafe
      // and should not be replicated for production purposes.
      try {
        const storedKeypair = await AsyncStorage.getItem(ASYNC_STORAGE_KEY);
        let keypair: Keypair;
        if (storedKeypair !== null) {
          const encodedKeypair: EncodedKeypair = JSON.parse(storedKeypair);
          keypair = decodeKeypair(encodedKeypair);
          console.log('Keypair retrieved: ' + keypair.publicKey);
        } else {
          // Generate new keypair
          keypair = await Keypair.generate();
          console.log('Keypair generated: ' + keypair.publicKey);
          await AsyncStorage.setItem(
            ASYNC_STORAGE_KEY,
            JSON.stringify(encodeKeypair(keypair)),
          );
        }
        setKeypair(keypair);
      } catch (error) {
        NativeModules.WalletLib.log('Error retrieving keypair');
      }
    };

    generateKeypair();
  }, []);

  return (
    <WalletContext.Provider value={{wallet: keypair}}>
      {children}
    </WalletContext.Provider>
  );
};

export default WalletProvider;
