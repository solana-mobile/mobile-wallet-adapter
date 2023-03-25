import React, { createContext, useContext, useState, useEffect } from 'react';
import { Keypair } from '@solana/web3.js';

interface WalletContextType {
  wallet: Keypair | null;
}

const WalletContext = createContext<WalletContextType>({ wallet: null });

export const useWallet = () => {
  return useContext(WalletContext);
};

type WalletProviderProps = {
    children: React.ReactNode;
}

const WalletProvider: React.FC<WalletProviderProps> = ({ children }) => {
  const [keypair1, setKeypair] = useState<Keypair | null>(null);

  useEffect(() => {
    const generateKeypair = async () => {
      const keypair = await Keypair.generate();
      setKeypair(keypair);
    };

    generateKeypair();
  }, []);

  return (
    <WalletContext.Provider value={{ wallet: keypair1 }}>
      {children}
    </WalletContext.Provider>
  );
};

export default WalletProvider;