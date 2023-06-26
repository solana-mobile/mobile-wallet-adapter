import React, {createContext, useContext} from 'react';
import { ClientTrustUseCase } from '../utils/ClientTrustUseCase';

interface CientTrustContextType {
  clientTrustUseCase: ClientTrustUseCase | null;
}

const ClientTrustContext = createContext<CientTrustContextType>({clientTrustUseCase: null});

export const useClientTrust = () => {
  return useContext(ClientTrustContext);
};

type ClientTrustProviderProps = {
  clientTrustUseCase: ClientTrustUseCase | null;
  children: React.ReactNode;
};

const ClientTrustProvider: React.FC<ClientTrustProviderProps> = ({clientTrustUseCase, children}) => {
  return (
    <ClientTrustContext.Provider value={{clientTrustUseCase: clientTrustUseCase}}>
      {children}
    </ClientTrustContext.Provider>
  );
};

export default ClientTrustProvider;
