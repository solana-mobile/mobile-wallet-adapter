import { css, type CSSResultGroup, html } from 'lit';

import { EmbeddedModalElement } from './modalElement.js';

export class LoadingSpinnerElement extends EmbeddedModalElement {
    static get styles(): CSSResultGroup {
        return css`
            .backdrop {
                position: absolute;
                width: 100%;
                height: 100%;
            }
            :host {
                --spinner-color: #ffffff;
            }
            .indicator:focus {
                outline: none;
            }
            .indicator {
                display: flex;
                justify-content: center;
                align-items: start;
                position: fixed;
                z-index: 1;
                left: 0;
                top: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0, 0, 0, 0.4);
                overflow-y: auto;
            }

            .container {
                display: flex;
                margin: auto;
            }

            .animation {
                position: relative;
                left: -9999px;
                width: 10px;
                height: 10px;
                border-radius: 5px;
                background-color: var(--spinner-color);
                color: var(--spinner-color);
                box-shadow:
                    9984px 0 0 0 var(--spinner-color),
                    9999px 0 0 0 var(--spinner-color),
                    10014px 0 0 0 var(--spinner-color);
                animation: dot-typing 1.5s infinite linear;
            }

            @keyframes dot-typing {
                0% {
                    box-shadow:
                        9984px 0 0 0 var(--spinner-color),
                        9999px 0 0 0 var(--spinner-color),
                        10014px 0 0 0 var(--spinner-color);
                }
                16.667% {
                    box-shadow:
                        9984px -10px 0 0 var(--spinner-color),
                        9999px 0 0 0 var(--spinner-color),
                        10014px 0 0 0 var(--spinner-color);
                }
                33.333% {
                    box-shadow:
                        9984px 0 0 0 var(--spinner-color),
                        9999px 0 0 0 var(--spinner-color),
                        10014px 0 0 0 var(--spinner-color);
                }
                50% {
                    box-shadow:
                        9984px 0 0 0 var(--spinner-color),
                        9999px -10px 0 0 var(--spinner-color),
                        10014px 0 0 0 var(--spinner-color);
                }
                66.667% {
                    box-shadow:
                        9984px 0 0 0 var(--spinner-color),
                        9999px 0 0 0 var(--spinner-color),
                        10014px 0 0 0 var(--spinner-color);
                }
                83.333% {
                    box-shadow:
                        9984px 0 0 0 var(--spinner-color),
                        9999px 0 0 0 var(--spinner-color),
                        10014px -10px 0 0 var(--spinner-color);
                }
                100% {
                    box-shadow:
                        9984px 0 0 0 var(--spinner-color),
                        9999px 0 0 0 var(--spinner-color),
                        10014px 0 0 0 var(--spinner-color);
                }
            }
        `;
    }

    render() {
        return html`
            <div class="indicator" role="dialog" aria-modal="true" aria-label="Connecting to wallet" tabindex="-1">
                <div class="backdrop" @click=${this.dismiss}></div>
                <div class="container">
                    <div class="animation"></div>
                </div>
            </div>
        `;
    }
}
