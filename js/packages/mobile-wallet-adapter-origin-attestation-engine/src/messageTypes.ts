export enum OriginAttestationMessageType {
    AttestOrigin = 'sms:attest-origin',
    EngineReady = 'sms:origin-attestation-engine-ready',
    GenerateOriginAttestationKeypair = 'sms:generate-origin-attestation-keypair',
    GrantOriginAttestationToken = 'sms:grant-origin-attestation-token',
    IssueOriginAttestationPublicKey = 'sms:issue-origin-attestation-public-key',
}
