import { MobileWallet, ProtocolVersion, SolanaCloneAuthorization, SolanaSignTransactions } from "./types";

export default function createMobileWalletProxy<TMethodName extends keyof MobileWallet>(
    protocolVersion: ProtocolVersion,
    protocolRequestHandler: (method: string, params: Parameters<MobileWallet[TMethodName]>[0]) => Promise<Object>
): MobileWallet {
    return new Proxy<MobileWallet>({} as MobileWallet, {
        get<TMethodName extends keyof MobileWallet>(target: MobileWallet, p: TMethodName) {
            if (target[p] == null) {
                target[p] = async function (inputParams: Parameters<MobileWallet[TMethodName]>[0]) {
                    const { method, params } = handleMobileWalletRequest(p, inputParams, protocolVersion);
                    const result = await protocolRequestHandler(method, params);
                    return handleMobileWalletResponse(p, result, protocolVersion);
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

function handleMobileWalletResponse<TMethodName extends keyof MobileWallet>(
    method: TMethodName, 
    response: any,
    protocolVersion: ProtocolVersion
) {
    const result = response;
    switch (method) {
        case 'getCapabilities': {
            switch (protocolVersion) {
                case 'legacy': {
                    result['features'] = [ SolanaSignTransactions ];
                    if (result['supports_clone_authorization'] == true) {
                        result['features'].push(SolanaCloneAuthorization);
                    }
                    break;
                }
                case 'v1': {
                    result['supports_sign_and_send_transactions'] = true;
                    result['supports_clone_authorization'] = result.features.indexOf(SolanaCloneAuthorization) > -1;
                    break;
                }
            }
            break;
        }
    }
    return result;
};