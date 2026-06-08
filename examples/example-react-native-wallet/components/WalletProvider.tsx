import AsyncStorage from '@react-native-async-storage/async-storage';
import { Keypair } from '@solana/web3.js';
import { base58FromUint8Array, base58ToUint8Array } from '@solana-mobile/mobile-wallet-adapter-protocol/encoding';
import React, { createContext, useContext, useEffect, useState } from 'react';

interface WalletContextType {
    wallet: Keypair | null;
}

const WalletContext = createContext<WalletContextType>({ wallet: null });

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
        secretKeyBase58: base58FromUint8Array(keypair.secretKey),
    };
};

const decodeKeypair = (keypair: EncodedKeypair): Keypair => {
    var secretKey: Uint8Array = base58ToUint8Array(keypair.secretKeyBase58);
    return Keypair.fromSecretKey(secretKey);
};

const WalletProvider: React.FC<WalletProviderProps> = ({ children }) => {
    const [keypair, setKeypair] = useState<Keypair | null>(null);

    useEffect(() => {
        const generateKeypair = async () => {
            // For testing purposes, we are storing the keypair in async-storage. This is unsafe
            // and should not be replicated for production purposes.
            try {
                const storedKeypair = await AsyncStorage.getItem(ASYNC_STORAGE_KEY);
                let nextKeypair: Keypair;
                if (storedKeypair !== null) {
                    const encodedKeypair: EncodedKeypair = JSON.parse(storedKeypair);
                    nextKeypair = decodeKeypair(encodedKeypair);
                    console.log('Keypair retrieved: ' + nextKeypair.publicKey);
                } else {
                    // Generate new keypair
                    nextKeypair = await Keypair.generate();
                    console.log('Keypair generated: ' + nextKeypair.publicKey);
                    await AsyncStorage.setItem(ASYNC_STORAGE_KEY, JSON.stringify(encodeKeypair(nextKeypair)));
                }
                setKeypair(nextKeypair);
            } catch {
                console.log('Error retrieving keypair');
            }
        };

        generateKeypair();
    }, []);

    return <WalletContext.Provider value={{ wallet: keypair }}>{children}</WalletContext.Provider>;
};

export default WalletProvider;
