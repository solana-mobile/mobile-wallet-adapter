import { Button } from '@mui/material';
import { useWallet } from '@solana/wallet-adapter-react';
import { ComponentProps } from 'react';
import useGuardedCallback from '../utils/useGuardedCallback';
import { SolanaSignInInput } from '@solana/wallet-standard-features';
import { verifySignIn } from '@solana/wallet-standard-util';

type Props = Readonly<ComponentProps<typeof Button>>;

export default function SignInButton(props: Props) {
    const { connected, signIn } = useWallet();
    const handleDisconnectClick = useGuardedCallback(async () => {
        if (signIn) {
            const input: SolanaSignInInput = {
                domain: window.location.host,
                statement: "Sign in to Example Web App",
                uri: window.location.origin,
            }
            const output = await signIn(input);
            if (!verifySignIn(input, output)) {
                throw new Error('Sign In verification failed!');
            }
        } else {
            throw new Error('Sign In not available, wallet does not support sign in');
        }
    }, [signIn]);
    return <Button {...props} disabled={connected} onClick={handleDisconnectClick} />;
}

