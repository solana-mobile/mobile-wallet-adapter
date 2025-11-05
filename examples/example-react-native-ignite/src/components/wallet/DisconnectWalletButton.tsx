import { KitMobileWallet, transact } from "@solana-mobile/mobile-wallet-adapter-protocol-kit"
import { FC } from "react"
import { ViewStyle } from "react-native"

import { Button } from "../Button"
import { useAppTheme } from "@/theme/context"
import { ThemedStyle } from "@/theme/types"
import { useAppContext } from "@/context/AppContext"

export const DisconnectWalletButton: FC = function DisconnectWalletButton() {
  const { themed } = useAppTheme()

  const { authToken, unSetWalletConnected, unSetAuthToken } = useAppContext()

  const connectWallet = async () => {
    await transact(async (wallet: KitMobileWallet) => {
      const authorizationResult = await wallet.deauthorize({
        auth_token: authToken!,
      })

      if (authorizationResult) {
        unSetWalletConnected()
        unSetAuthToken()
      }
    })
  }

  return (
    <Button onPress={connectWallet} style={themed($connectWalletButtonStyle)}>
      Disconnect Wallet
    </Button>
  )
}

const $connectWalletButtonStyle: ThemedStyle<ViewStyle> = ({ spacing, colors }) => ({
  padding: spacing.md,
  backgroundColor: colors.tintInactive,
})
