import { Button } from '@mui/material';
import { useWallet } from '@solana/wallet-adapter-react';
import { ComponentProps, useCallback } from 'react';
import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<ComponentProps<typeof Button>>;

export default function DisconnectButton(props: Props) {
    const { connected, disconnect } = useWallet();
    const handleDisconnectClick = useGuardedCallback(disconnect, [disconnect]);
    return <Button {...props} disabled={!connected} onClick={handleDisconnectClick} />;
}
