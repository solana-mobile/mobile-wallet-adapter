import { FC } from "react"
import { useAppContext } from "@/context/AppContext"

import { Screen } from "@/components/Screen"
import { $styles } from "@/theme/styles"
import { WalletConnectButton } from "@/components/wallet/ConnectWalletButton"
import { DisconnectWalletButton } from "@/components/wallet/DisconnectWalletButton"

export const WelcomeScreen: FC = function WelcomeScreen() {
  const { walletConnected } = useAppContext()

  return (
    <Screen preset="fixed" contentContainerStyle={$styles.flex1}>
      {!walletConnected ? <WalletConnectButton /> : <DisconnectWalletButton />}
    </Screen>
  )
}
