import { css, type CSSResultGroup, html, LitElement, nothing } from 'lit';

const FONT_STYLESHEET_ID = 'mobile-wallet-adapter-embedded-modal-font';
const FONT_STYLESHEET_URL =
    'https://fonts.googleapis.com/css2?family=Inter+Tight:ital,wght@0,100..900;1,100..900&display=swap';

// Font faces can't be declared inside a shadow root, so the stylesheet goes in the document head.
function loadFont() {
    if (document.getElementById(FONT_STYLESHEET_ID)) return;
    const link = document.createElement('link');
    link.id = FONT_STYLESHEET_ID;
    link.rel = 'stylesheet';
    link.href = FONT_STYLESHEET_URL;
    document.head.appendChild(link);
}

/**
 * Base for the embedded modals. Escape, a backdrop click, the close button and a page load
 * all dismiss the modal through {@link onDismiss}; the controller owning the element decides
 * what dismissal means.
 */
export class EmbeddedModalElement extends LitElement {
    // Statics in these elements are getters, not fields, so the package stays tree-shakeable.
    static get shadowRootOptions(): ShadowRootInit {
        return { ...LitElement.shadowRootOptions, mode: 'closed' };
    }

    static get styles(): CSSResultGroup {
        return css`
            .container {
                display: flex;
                justify-content: center;
                align-items: center;
                position: fixed;
                z-index: 2147483647;
                left: 0;
                top: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0, 0, 0, 0.4);
                overflow-y: auto;
            }
            .backdrop {
                position: absolute;
                width: 100%;
                height: 100%;
            }
            .card {
                display: flex;
                flex-direction: column;
                margin: auto 20px;
                max-width: 780px;
                padding: 20px;
                border-radius: 24px;
                background: #ffffff;
                font-family: 'Inter Tight', 'PT Sans', Calibri, sans-serif;
                transform: translateY(-200%);
                animation: slide-in 0.5s forwards;
            }
            @keyframes slide-in {
                100% {
                    transform: translateY(0%);
                }
            }
            .close {
                display: flex;
                align-items: center;
                justify-content: center;
                width: 32px;
                height: 32px;
                cursor: pointer;
                background: #e4e9e9;
                border: none;
                border-radius: 50%;
            }
            .close:focus-visible {
                outline-color: red;
            }
            .close svg {
                fill: #546266;
                transition: fill 200ms ease 0s;
            }
            .close:hover svg {
                fill: #fff;
            }
            .action {
                display: block;
                width: 100%;
                height: 56px;
                font-size: 1.25em;
                background: #000000;
                color: #ffffff;
                border-radius: 18px;
            }
        `;
    }

    /** Called with the triggering event when the user dismisses the modal. */
    onDismiss?: (event: Event) => void;

    /** Whether the card shows a close button. */
    protected closable = true;

    protected dismiss = (event: Event) => this.onDismiss?.(event);

    #onKeyDown = (event: KeyboardEvent) => {
        if (event.key === 'Escape') this.dismiss(event);
    };

    connectedCallback() {
        super.connectedCallback();
        loadFont();
        document.addEventListener('keydown', this.#onKeyDown);
        window.addEventListener('load', this.dismiss);
    }

    disconnectedCallback() {
        document.removeEventListener('keydown', this.#onKeyDown);
        window.removeEventListener('load', this.dismiss);
        super.disconnectedCallback();
    }

    protected renderContent(): unknown {
        return nothing;
    }

    render() {
        return html`
            <div class="container" role="dialog" aria-modal="true" aria-labelledby="title">
                <div class="backdrop" @click=${this.dismiss}></div>
                <div class="card">
                    <div>
                        ${
                            this.closable
                                ? html`<button class="close" aria-label="Close" @click=${this.dismiss}>
                                      <svg width="14" height="14">
                                          <path
                                              d="M 6.7125,8.3036995 1.9082,13.108199 c -0.2113,0.2112 -0.4765,0.3168 -0.7957,0.3168 -0.3192,0 -0.5844,-0.1056 -0.7958,-0.3168 C 0.1056,12.896899 0,12.631699 0,12.312499 c 0,-0.3192 0.1056,-0.5844 0.3167,-0.7958 L 5.1212,6.7124995 0.3167,1.9082 C 0.1056,1.6969 0,1.4317 0,1.1125 0,0.7933 0.1056,0.5281 0.3167,0.3167 0.5281,0.1056 0.7933,0 1.1125,0 1.4317,0 1.6969,0.1056 1.9082,0.3167 L 6.7125,5.1212 11.5167,0.3167 C 11.7281,0.1056 11.9933,0 12.3125,0 c 0.3192,0 0.5844,0.1056 0.7957,0.3167 0.2112,0.2114 0.3168,0.4766 0.3168,0.7958 0,0.3192 -0.1056,0.5844 -0.3168,0.7957 L 8.3037001,6.7124995 13.1082,11.516699 c 0.2112,0.2114 0.3168,0.4766 0.3168,0.7958 0,0.3192 -0.1056,0.5844 -0.3168,0.7957 -0.2113,0.2112 -0.4765,0.3168 -0.7957,0.3168 -0.3192,0 -0.5844,-0.1056 -0.7958,-0.3168 z"
                                          />
                                      </svg>
                                  </button>`
                                : nothing
                        }
                    </div>
                    <div class="content">${this.renderContent()}</div>
                </div>
            </div>
        `;
    }
}
