import { KitMobileWallet, transact } from "@solana-mobile/mobile-wallet-adapter-protocol-kit"
import { FC } from "react"
import { ViewStyle } from "react-native"

import { Button } from "../Button"
import { useAppTheme } from "@/theme/context"
import { ThemedStyle } from "@/theme/types"
import { useAppContext } from "@/context/AppContext"
import { getPublicKeyFromAddress } from "@/utils/getPublicKeyFromAddress"

const APP_IDENTITY = {
  name: "Expo app",
  uri: "https://expo.com",
  icon: "favicon.ico",
}

export const WalletConnectButton: FC = function WalletConnectButton() {
  const { themed } = useAppTheme()

  const { setWalletConnected, setPublicKey, setAuthToken } = useAppContext()

  const connectWallet = async () => {
    await transact(async (wallet: KitMobileWallet) => {
      const authorizationResult = await wallet.authorize({
        chain: "devnet",
        identity: APP_IDENTITY,
      })

      console.log("Authorization result:", authorizationResult)

      if (authorizationResult) {
        setWalletConnected()
      }

      const publicKey = getPublicKeyFromAddress(authorizationResult.accounts[0].address)
      setPublicKey(publicKey)
      setAuthToken(authorizationResult.auth_token)
    })
  }

  return (
    <Button onPress={connectWallet} style={themed($connectWalletButtonStyle)}>
      connect wallet
    </Button>
  )
}

const $connectWalletButtonStyle: ThemedStyle<ViewStyle> = ({ spacing, colors }) => ({
  padding: spacing.md,
  backgroundColor: colors.tint,
})
