package com.solana.mobilewalletadapter.walletlib.authorization;

/*package*/ interface AuthorizationsDaoInterface {

    long insert(int id, long timeStamp, int publicKeyId, String cluster, int walletUriBaseId, byte[] scope);

    int deleteByAuthRecordId(int authRecordId);

    void deleteByIdentityRecordId(int identityRecordId);
}
