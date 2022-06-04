/**
 * Properties that wallets may present to users when an app
 * asks for authorization to execute privileged methods (see
 * {@link PrivilegedMethods}).
 */
export type AppIdentity = Readonly<{
    uri?: string;
    icon?: string;
    name?: string;
}>;

/**
 * An ephemeral elliptic-curve keypair on the P-256 curve.
 * This public key is used to create the association token.
 * The private key is used during session establishment.
 */
export type AssociationKeypair = CryptoKeyPair;

/**
 * RPC methods for which the mobile wallet requires authorization.
 */
export type PrivilegedMethods = 'sign_and_send_transaction' | 'sign_message' | 'sign_transaction';

export interface MobileWallet {
    (
        method: 'authorize',
        params: {
            identity: AppIdentity;
            privileged_methods: PrivilegedMethods[];
        },
    ): Promise<
        Readonly<{
            auth_token: string;
            pub_key: string;
            wallet_uri_base: string;
        }>
    >;
}
