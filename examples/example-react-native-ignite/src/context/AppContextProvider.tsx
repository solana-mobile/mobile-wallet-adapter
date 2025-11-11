import { useState } from "react"
import { AppContext } from "./AppContext"

export const AppContextProvider = ({ children }: { children: React.ReactNode }) => {
  const [walletConnected, setWalletConnected] = useState(false)
  const [publicKey, setpublicKey] = useState<string | undefined>(undefined)
  const [authToken, setauthToken] = useState<string | undefined>(undefined)

  const setWalletConnectedTrue = () => {
    setWalletConnected(true)
  }
  const unSetWalletConnectedFalse = () => {
    setWalletConnected(false)
  }

  const setPublicKey = (key: string) => {
    setpublicKey(key)
  }

  const setAuthToken = (token: string) => {
    setauthToken(token)
  }
  const unSetAuthToken = () => {
    setauthToken(undefined)
  }

  return (
    <AppContext.Provider
      value={{
        walletConnected,
        setWalletConnected: setWalletConnectedTrue,
        unSetWalletConnected: unSetWalletConnectedFalse,
        publicKey,
        setPublicKey,
        authToken,
        setAuthToken,
        unSetAuthToken,
      }}
    >
      {children}
    </AppContext.Provider>
  )
}
