import React, { createContext } from "react"

type AppContextType = {
  walletConnected: boolean
  setWalletConnected: () => void
  unSetWalletConnected: () => void

  publicKey: string | undefined
  setPublicKey: (key: string) => void

  authToken: string | undefined
  setAuthToken: (token: string) => void
  unSetAuthToken: () => void
}

export const AppContext = createContext<AppContextType | undefined>(undefined)

export const useAppContext = () => {
  const context = React.useContext(AppContext)
  if (!context) {
    throw new Error("Error in the usage of AppContextProvider")
  }
  return context
}
