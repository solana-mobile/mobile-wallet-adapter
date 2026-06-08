import 'fast-text-encoding';

import {
    AuthorizeDappCompleteResponse,
    AuthorizeDappRequest,
    MWARequestFailReason,
    resolve,
} from '@solana-mobile/mobile-wallet-adapter-walletlib';
import React, { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Button } from 'react-native-paper';

import { useClientTrust } from '../components/ClientTrustProvider';
import MWABottomsheetHeader from '../components/MWABottomsheetHeader';
import { useWallet } from '../components/WalletProvider';
import { VerificationInProgress, VerificationState } from '../utils/ClientTrustUseCase';

interface AuthenticationScreenProps {
    request: AuthorizeDappRequest;
}

export default function AuthenticationScreen({ request }: AuthenticationScreenProps) {
    const { wallet } = useWallet();
    const { clientTrustUseCase } = useClientTrust();
    const [verificationState, setVerificationState] = useState<VerificationState | undefined>(undefined);

    // We should always have an available keypair here.
    if (!wallet) {
        throw new Error('Wallet is null or undefined');
    }

    useEffect(() => {
        const verifyClient = async () => {
            const nextVerificationState = await clientTrustUseCase?.verifyAuthorizationSource(
                request.appIdentity?.identityUri,
            );
            setVerificationState(nextVerificationState);
        };

        verifyClient();
    }, [clientTrustUseCase, request.appIdentity?.identityUri]);

    return (
        <View>
            <MWABottomsheetHeader
                title={'Authorize Dapp'}
                cluster={request.chain}
                appIdentity={request.appIdentity}
                verificationState={verificationState ?? new VerificationInProgress('')}
            />
            <View style={styles.buttonGroup}>
                <Button
                    style={styles.actionButton}
                    onPress={() => {
                        resolve(request, {
                            accounts: [
                                {
                                    publicKey: wallet.publicKey.toBytes(),
                                    accountLabel: 'Backpack',
                                    icon: 'data:text/plain;base64',
                                    chains: ['solana:devnet', 'solana:testnet'],
                                    features: ['solana:signTransactions'],
                                },
                            ],
                            authorizationScope: new TextEncoder().encode(verificationState?.authorizationScope),
                        } as AuthorizeDappCompleteResponse);
                    }}
                    mode="contained"
                >
                    Authorize
                </Button>
                <Button
                    style={styles.actionButton}
                    onPress={() => {
                        resolve(request, { failReason: MWARequestFailReason.UserDeclined });
                    }}
                    mode="outlined"
                >
                    Decline
                </Button>
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    buttonGroup: {
        display: 'flex',
        flexDirection: 'row',
        width: '100%',
    },
    actionButton: {
        flex: 1,
        marginEnd: 8,
    },
});
