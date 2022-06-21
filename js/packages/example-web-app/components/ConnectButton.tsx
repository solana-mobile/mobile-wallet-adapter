import { Button } from '@mui/material';
import { useWallet } from '@solana/wallet-adapter-react';
import { ComponentProps } from 'react';

import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<ComponentProps<typeof Button>>;

export default function ConnectButton(props: Props) {
    const { connect, connected } = useWallet();
    const handleConnectClick = useGuardedCallback(connect, [connect]);
    return <Button {...props} disabled={connected} onClick={handleConnectClick} />;
}
