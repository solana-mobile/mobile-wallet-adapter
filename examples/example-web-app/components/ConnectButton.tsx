import { Button } from '@mui/material';
import { ComponentProps, useCallback } from 'react';
import { useWallet } from '@solana/wallet-adapter-react';
import { useWalletModal } from '@solana/wallet-adapter-react-ui';
import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<ComponentProps<typeof Button>>;

export default function ConnectButton(props: Props) {
    const { connect, connected, wallet } = useWallet();
    const { setVisible: showWalletSelectionModal } = useWalletModal();
    const handleConnectClick = useGuardedCallback(connect, [connect]);
    if (wallet != null) {
        return <Button {...props} disabled={connected} onClick={handleConnectClick} />;
    } else {
        return (
            <Button {...props} onClick={() => showWalletSelectionModal(true)}>
                Select Wallet
            </Button>
        );
    }
}
