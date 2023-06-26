import 'react-native-url-polyfill/auto'
import { 
    verifyCallingPackage, 
    getCallingPackageUid
} from '@solana-mobile/mobile-wallet-adapter-walletlib';

export class ClientTrustUseCase {
    
    private static SCOPE_DELIMITER = ',';

    static readonly LOCAL_PATH_SUFFIX = 'v1/associate/local';
    static readonly REMOTE_PATH_SUFFIX = 'v1/associate/remote';

    readonly callingPackage?: string;
    readonly associationType!: AssociationType;

    constructor(associationUri: string, callingPackage?: string) {
        this.callingPackage = callingPackage
        this.associationType = this.getAssociationType(associationUri)
    }

    async verifyAuthorizationSource(clientIdentityUri?: string): Promise<VerificationState> {
        switch (this.associationType) {
            case AssociationType.LocalFromBrowser: 
                if (clientIdentityUri != null) {
                    // TODO: kick off web-based client verification here
                    await setTimeout(() => {}, 1500); // fake this operation taking a little while
                    console.debug('Web-scoped authorization verification not yet implemented');
                    return new VerificationSucceeded(AssociationType.LocalFromBrowser, new URL(clientIdentityUri).host)
                } else {
                    console.debug('Client did not provide an identity URI; not verifiable');
                    return new NotVerifiable(AssociationType.LocalFromBrowser)
                }
            case AssociationType.LocalFromApp: 
                if (clientIdentityUri != null) {
                    const verified = await verifyCallingPackage(clientIdentityUri) // NOTE: AssociationType.LOCAL_FROM_APP implies that callingPackage is not null
                    if (verified) {
                        const uid = await getCallingPackageUid()
                        console.debug(`App-scoped authorization succeeded. UID: '${uid}'`)
                        return new VerificationSucceeded(AssociationType.LocalFromApp, uid)
                    } else {
                        console.log(`App-scoped authorization failed for '${clientIdentityUri}'`)
                        return new VerificationFailed(AssociationType.LocalFromApp)
                    }
                } else {
                    console.debug('Client did not provide an identity URI; not verifiable');
                    return new NotVerifiable(AssociationType.LocalFromApp)
                }
            case AssociationType.Remote: 
                console.log('Remote authorizations are not verifiable');
                return new NotVerifiable(AssociationType.Remote)
        }
    }

    async verifyReauthorizationSource(authorizationScope: string, clientIdentityUri?: string): Promise<VerificationState> {
        if (!authorizationScope.startsWith(this.associationType)) {
            console.warn('Reauthorization failed; association type mismatch');
            return new VerificationFailed(this.associationType);
        } else if (authorizationScope.length == this.associationType.length) {
            console.debug('Unqualified authorization scopes are not verifiable');
            return new NotVerifiable(this.associationType);
        } else {
            return this.verifyAuthorizationSource(clientIdentityUri)
        }
    }

    // Note: the authorizationScope and clientIdentityUri parameters should be retrieved from a
    // trusted source (e.g. a local database), as they are used as part of request verification.
    // When used with requests originating from walletlib, these parameters will originate in
    // AuthRepository, which is backed by a local database.
    async verifyPrivaledgedMethodSource(authorizationScope: string, clientIdentityUri?: string): Promise<boolean> {
        if (authorizationScope.startsWith(AssociationType.LocalFromBrowser)) {
            if (this.associationType != AssociationType.LocalFromBrowser) {
                console.warn('Attempt to use a web-scoped authorization with a non-web client');
                return false
            } else if (authorizationScope.length == AssociationType.LocalFromBrowser.length) {
                console.debug('Unqualified web-scoped authorization, continuing');
                return true
            } else if (authorizationScope[AssociationType.LocalFromBrowser.length] != ClientTrustUseCase.SCOPE_DELIMITER) {
                console.warn(`Unexpected character '${authorizationScope[AssociationType.LocalFromBrowser.length]}' in scope; expected '${ClientTrustUseCase.SCOPE_DELIMITER}'`)
                return false
            } else {
                console.debug('Treating qualified web-scoped authorization as a bearer token, continuing');
                return true
            }
        } else if (authorizationScope.startsWith(AssociationType.LocalFromApp)) {
            if (this.associationType != AssociationType.LocalFromApp) {
                console.warn('Attempt to use an app-scoped authorization with a non-app client');
                return false
            } else if (authorizationScope.length == AssociationType.LocalFromApp.length) {
                console.debug('Unqualified app-scoped authorization, continuing');
                return true
            } else if (authorizationScope[AssociationType.LocalFromApp.length] != ClientTrustUseCase.SCOPE_DELIMITER) {
                console.warn(`Unexpected character '${authorizationScope[AssociationType.LocalFromApp.length]}' in scope; expected '${ClientTrustUseCase.SCOPE_DELIMITER}'`);
                return false
            } else {
                var scopeUid: number;
                try {
                    scopeUid = Number(authorizationScope.substring(AssociationType.LocalFromApp.length + 1))
                } catch (e) {
                    console.warn('App-scoped authorization has invalid UID');
                    return false
                }

                var callingUid: number;
                try {
                    callingUid = await getCallingPackageUid()
                } catch (e) {
                    console.warn('Calling package is invalid');
                    return false
                }

                if (scopeUid == callingUid) {
                    console.debug('App-scoped authorization matches calling identity, continuing');
                    return true
                } else {
                    console.warn('App-scoped authorization does not match calling identity');
                    return false
                }
            }
        } else if (authorizationScope == AssociationType.Remote) {
            if (this.associationType != AssociationType.Remote) {
                console.warn('Attempt to use a remote-scoped authorization with a local client');
                return false
            } else {
                console.debug('Authorization with remote source, continuing');
                return true
            }
        } else {
            console.warn('Unknown authorization scope');
            return false
        }
    }

    private getAssociationType(associationUri: string): AssociationType {
        const parsedUri = associationUri.split('?')[0]//decodeURI(associationUri)
        if (parsedUri.endsWith(ClientTrustUseCase.LOCAL_PATH_SUFFIX)) {
            if (this.callingPackage != null) return AssociationType.LocalFromApp
            else return AssociationType.LocalFromBrowser
        } else if (parsedUri.endsWith(ClientTrustUseCase.REMOTE_PATH_SUFFIX)) {
            return AssociationType.Remote
        } else {
            throw new Error(`Unrecognized association URI type. Provided URI = ${parsedUri}`)
        }
    }
}

enum AssociationType {
    LocalFromBrowser = "web",
    LocalFromApp = "app",
    Remote = "rem"
}

abstract class VerificationStateBase {
    private static SCOPE_DELIMITER = ',';
    private scopeTag: string;
    private qualifier?: string;
    readonly authorizationScope!: string

    constructor(scopeTag: string, qualifier?: string) {
        this.scopeTag = scopeTag
        this.qualifier = qualifier
        this.authorizationScope = qualifier == null ? scopeTag : `${scopeTag}${VerificationStateBase.SCOPE_DELIMITER}${qualifier}`
    }
}

export class VerificationInProgress extends VerificationStateBase {
    constructor(scopeTag: string) {
        super(scopeTag);
    }
}

export class VerificationSucceeded extends VerificationStateBase {
    constructor(scopeTag: string, qualifier: string) {
        super(scopeTag, qualifier);
    }
}

export class VerificationFailed extends VerificationStateBase {
    constructor(scopeTag: string) {
        super(scopeTag);
    }
}

export class NotVerifiable extends VerificationStateBase {
    constructor(scopeTag: string) {
        super(scopeTag);
    }
}

export type VerificationState = 
    | VerificationInProgress 
    | VerificationSucceeded 
    | VerificationFailed 
    | NotVerifiable

