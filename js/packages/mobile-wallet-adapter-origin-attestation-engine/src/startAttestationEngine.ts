import getAttestee from './getAttestee';
import { OriginAttestationMessageType } from './messageTypes';
import { Message } from './types';

function retrievePrivateKey() {
    return JSON.parse(globalThis.window.localStorage.getItem('Q') as string);
}

function storePrivateKey(Q: number[]) {
    globalThis.window.localStorage.setItem('Q', JSON.stringify(Q));
}

async function receiveNextMessage(attestee: ReturnType<typeof getAttestee>) {
    const abortController = new AbortController();
    const receivedMessage = await Promise.race([
        attestee.getPromiseForMessage(OriginAttestationMessageType.AttestOrigin, abortController.signal),
        attestee.getPromiseForMessage(
            OriginAttestationMessageType.GenerateOriginAttestationKeypair,
            abortController.signal,
        ),
    ]);
    abortController.abort();
    return receivedMessage;
}

async function handleMessage(message: Message, attestee: ReturnType<typeof getAttestee>) {
    switch (message.__type) {
        case OriginAttestationMessageType.AttestOrigin:
            if (
                // TODO: replace with attestation verification
                message.hi !== 'there'
            ) {
                throw new Error('TODO: Origin attestation failed verification');
            }
            attestee.sendMessage({
                __type: OriginAttestationMessageType.GrantOriginAttestationToken,
                // TODO: Generate actual attestation token.
                originAttestationToken: `If this were finished I would have prepared you an attestation token using the keypair: ${retrievePrivateKey()}`,
            });
            break;
        case OriginAttestationMessageType.GenerateOriginAttestationKeypair: {
            // TODO: Generate actual attestation keypair
            const [Q, d] = [Date.now() % 127, Date.now() % 256];
            storePrivateKey([Q, d]);
            attestee.sendMessage({
                __type: OriginAttestationMessageType.IssueOriginAttestationPublicKey,
                publicKey: new Uint8Array([d]),
            });
            break;
        }
    }
}

export default async function startAttestationEngine(): Promise<boolean> {
    const attestee = getAttestee();
    attestee.sendMessage({ __type: OriginAttestationMessageType.EngineReady });
    while (
        // eslint-disable-next-line no-constant-condition
        true
    ) {
        const message = await receiveNextMessage(attestee);
        await handleMessage(message, attestee);
    }
}
