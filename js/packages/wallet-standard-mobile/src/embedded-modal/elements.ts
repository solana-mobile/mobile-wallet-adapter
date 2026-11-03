import { ErrorModalElement } from './elements/errorModalElement.js';
import { LoadingSpinnerElement } from './elements/loadingSpinnerElement.js';
import { LocalConnectionModalElement } from './elements/localConnectionModalElement.js';
import { LoopbackBlockedModalElement } from './elements/loopbackBlockedModalElement.js';
import { LoopbackPermissionModalElement } from './elements/loopbackPermissionModalElement.js';
import { RemoteConnectionModalElement } from './elements/remoteConnectionModalElement.js';

const ELEMENTS = {
    'mobile-wallet-adapter-error-modal': ErrorModalElement,
    'mobile-wallet-adapter-loading-spinner': LoadingSpinnerElement,
    'mobile-wallet-adapter-local-connection-modal': LocalConnectionModalElement,
    'mobile-wallet-adapter-loopback-blocked-modal': LoopbackBlockedModalElement,
    'mobile-wallet-adapter-loopback-permission-modal': LoopbackPermissionModalElement,
    'mobile-wallet-adapter-remote-connection-modal': RemoteConnectionModalElement,
};

export type EmbeddedModalElements = { [Tag in keyof typeof ELEMENTS]: InstanceType<(typeof ELEMENTS)[Tag]> };
export type EmbeddedModalTag = keyof EmbeddedModalElements;

/** Creates an embedded modal element, registering its custom element on first use. */
export function createEmbeddedModalElement<Tag extends EmbeddedModalTag>(tag: Tag): EmbeddedModalElements[Tag] {
    if (!customElements.get(tag)) customElements.define(tag, ELEMENTS[tag]);
    return document.createElement(tag) as EmbeddedModalElements[Tag];
}
