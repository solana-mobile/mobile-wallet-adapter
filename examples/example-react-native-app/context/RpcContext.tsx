import type { Rpc, RpcSubscriptions, SolanaRpcApiDevnet, SolanaRpcSubscriptionsApi } from '@solana/kit';
import { createSolanaRpc, createSolanaRpcSubscriptions, devnet } from '@solana/kit';
import { createContext } from 'react';

export const RpcContext = createContext<{
    rpc: Rpc<SolanaRpcApiDevnet>;
    rpcSubscriptions: RpcSubscriptions<SolanaRpcSubscriptionsApi>;
}>({
    rpc: createSolanaRpc(devnet('https://api.devnet.solana.com')),
    rpcSubscriptions: createSolanaRpcSubscriptions(devnet('wss://api.devnet.solana.com')),
});