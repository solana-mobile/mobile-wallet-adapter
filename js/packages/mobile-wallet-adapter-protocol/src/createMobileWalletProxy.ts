import { createSIWSMessageBase64 } from "./createSIWSMessage";
import { 
    AuthorizationResult, 
    MobileWallet, 
    ProtocolVersion, 
    SignInPayload, 
    SignInResult, 
    SolanaCloneAuthorization, 
    SolanaSignTransactions 
} from "./types";
import type { IdentifierArray } from "@wallet-standard/core";

/**
 * Creates a {@link MobileWallet} proxy that handles backwards compatibility and API to RPC conversion.
 * 
 * @param protocolVersion the protocol version in use for this session/request
 * @param protocolRequestHandler callback function that handles sending the RPC request to the wallet endpoint.
 * @returns a {@link MobileWallet} proxy
 */
export default function createMobileWalletProxy<
    TMethodName extends keyof MobileWallet, 
    TReturn extends Awaited<ReturnType<MobileWallet[TMethodName]>>
>(
    protocolVersion: ProtocolVersion,
    protocolRequestHandler: (method: string, params: Parameters<MobileWallet[TMethodName]>[0]) => Promise<any>
): MobileWallet {
    return new Proxy<MobileWallet>({} as MobileWallet, {
        get<TMethodName extends keyof MobileWallet>(target: MobileWallet, p: TMethodName) {
            // Wrapping a Proxy in a promise results in the Proxy being asked for a 'then' property so must 
            // return null if 'then' is called on this proxy to let the 'resolve()' call know this is not a promise.
            // see: https://stackoverflow.com/a/53890904
            //@ts-ignore
            if (p === 'then') { 
                return null;
            }
            if (target[p] == null) {
                target[p] = async function (inputParams: Parameters<MobileWallet[TMethodName]>[0]) {
                    const { method, params } = handleMobileWalletRequest(p, inputParams, protocolVersion);
                    const result = await protocolRequestHandler(method, params) as Awaited<ReturnType<MobileWallet[TMethodName]>>;
                    // if the request tried to sign in but the wallet did not return a sign in result, fallback on message signing
                    if (method === 'authorize' && (params as any).sign_in_payload && !(result as any).sign_in_result) {
                        (result as any)['sign_in_result'] = await signInFallback(
                            (params as Parameters<MobileWallet['authorize']>[0]).sign_in_payload as SignInPayload, 
                            result as Awaited<ReturnType<MobileWallet['authorize']>>, 
                            protocolRequestHandler
                        );
                    }
                    return handleMobileWalletResponse(p, result, protocolVersion) as TReturn;
                } as MobileWallet[TMethodName];
            }
            return target[p];
        },
        defineProperty() {
            return false;
        },
        deleteProperty() {
            return false;
        },
    });
};

/**
 * Handles all {@link MobileWallet} API requests and determines the correct MWA RPC method and params to call.
 * This handles backwards compatibility, based on the provided @protocolVersion. 
 * 
 * @param methodName the name of {@link MobileWallet} method that was called
 * @param methodParams the parameters that were passed to the method
 * @param protocolVersion the protocol version in use for this session/request
 * @returns the RPC request method and params that should be sent to the wallet endpoint 
 */
function handleMobileWalletRequest<TMethodName extends keyof MobileWallet>(
    methodName: TMethodName, 
    methodParams: Parameters<MobileWallet[TMethodName]>[0],
    protocolVersion: ProtocolVersion
) {
    let params = methodParams;
    let method: string = methodName
        .toString()
        .replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`)
        .toLowerCase();
    switch (methodName) {
        case 'authorize': {
            let { chain } = params as Parameters<MobileWallet['authorize']>[0];
            if (protocolVersion === 'legacy') {
                switch (chain) {
                    case 'solana:testnet': { chain = 'testnet'; break; }
                    case 'solana:devnet': { chain = 'devnet'; break; }
                    case 'solana:mainnet': { chain = 'mainnet-beta'; break; }
                    default: { chain = (params as any).cluster; }
                }
                (params as any).cluster = chain;
            } else {
                switch (chain) {
                    case 'testnet':
                    case 'devnet': { chain = `solana:${chain}`; break; }
                    case 'mainnet-beta': { chain = 'solana:mainnet'; break; }
                }
                (params as Parameters<MobileWallet['authorize']>[0]).chain = chain;
            }
        }
        case 'reauthorize': {
            const { auth_token, identity } = params as Parameters<MobileWallet['authorize' | 'reauthorize']>[0];
            if (auth_token) {
                switch (protocolVersion) {
                    case 'legacy': {
                        method = 'reauthorize';
                        params = { auth_token: auth_token, identity: identity };
                        break;
                    }
                    default: {
                        method = 'authorize';
                        break;
                    }
                }
            }
            break;
        }
    }
    return { method, params }
};

/**
 * Handles all {@link MobileWallet} API responses and modifies the response for backwards compatibility, if needed 
 * 
 * @param method the {@link MobileWallet} method that was called
 * @param response the original response that was returned by the method call
 * @param protocolVersion the protocol version in use for this session/request
 * @returns the possibly modified response 
 */
function handleMobileWalletResponse<TMethodName extends keyof MobileWallet>(
    method: TMethodName, 
    response: Awaited<ReturnType<MobileWallet[TMethodName]>>,
    protocolVersion: ProtocolVersion
): Awaited<ReturnType<MobileWallet[TMethodName]>> {
    switch (method) {
        case 'getCapabilities': {
            const capabilities = response as Awaited<ReturnType<MobileWallet['getCapabilities']>>
            switch (protocolVersion) {
                case 'legacy': {
                    const features: `${string}:${string}`[] = [ SolanaSignTransactions ];
                    if (capabilities.supports_clone_authorization === true) {
                        features.push(SolanaCloneAuthorization);
                    }
                    return {
                        ...capabilities,
                        features: features as IdentifierArray,
                    } as Awaited<ReturnType<MobileWallet[TMethodName]>>;
                }
                case 'v1': {
                    return {
                        ...capabilities,
                        supports_sign_and_send_transactions: true,
                        supports_clone_authorization: capabilities.features.includes(SolanaCloneAuthorization)
                    } as Awaited<ReturnType<MobileWallet[TMethodName]>>;
                }
            }
        }
    }
    return response;
};

async function signInFallback(
    signInPayload: SignInPayload,
    authorizationResult: Awaited<ReturnType<MobileWallet['authorize']>>,
    protocolRequestHandler: (method: string, params: Parameters<MobileWallet['signMessages']>[0]) => Promise<unknown>
) {
    const domain = signInPayload.domain ?? window.location.host;
    const address = (authorizationResult as AuthorizationResult).accounts[0].address;
    const siwsMessage = createSIWSMessageBase64({ ...signInPayload, domain, address })
    const signMessageResult = await (protocolRequestHandler('sign_messages', 
        { 
            addresses: [ address ], 
            payloads: [ siwsMessage ]
        }
    ) as ReturnType<MobileWallet['signMessages']>);
    const signInResult: SignInResult = {
        address: address,
        signed_message: siwsMessage,
        signature: signMessageResult.signed_payloads[0].slice(siwsMessage.length)
    };
    return signInResult;
}