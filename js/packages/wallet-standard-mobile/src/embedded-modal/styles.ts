export const css = `
.mwa-modal-container {
    display: flex; /* Use flexbox to center content */
    justify-content: center; /* Center horizontally */
    align-items: center; /* Center vertically */
    position: fixed; /* Stay in place */
    z-index: 1; /* Sit on top */
    left: 0;
    top: 0;
    width: 100%; /* Full width */
    height: 100%; /* Full height */
    background-color: rgba(0,0,0,0.4); /* Black w/ opacity */
}

.mwa-modal-card {
    display: flex;
    flex-direction: column;
    margin: auto 20px;
    max-width: 780px;
    padding: 20px;
    border-radius: 24px;
    background: #ffffff;
    font-family: Arial, Verdana, sans-serif;
}

.mwa-modal-card> div:nth-child(2) {
    display: flex; 
    /*border: 1px solid blue;*/
    margin-top: 40px;
    padding: 10px;
}

.mwa-modal-card > div:nth-child(2) > div:first-child {
    display: flex;
    flex-direction: column;
    flex: 2;
    margin-right: 30px;
}

.mwa-modal-card > div:nth-child(2) > div:nth-child(2) {
    display: flex;
    flex-direction: column;
    flex: 1;
    margin-left: auto;
}

.mwa-modal-card > div:nth-child(4) {
    display: flex;
    padding: 10px;
}

.mwa-modal-close {
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

.mwa-modal-close:focus-visible {
    outline-color: red;
}

.mwa-modal-close svg {
    fill: #546266;
    transition: fill 200ms ease 0s;
}

.mwa-modal-close:hover svg {
    fill: #fff;
}

.mwa-modal-icon {}

.mwa-modal-title {
    color: #000000;
    font-size: 2.5em;
    font-weight: 600;
}

.mwa-modal-qr-label {
    text-align: right;
    color: #000000;
}

.mwa-modal-qr-code-container {
    margin-left: auto;
}

.mwa-modal-divider {
    margin-top: 20px;
    padding-left: 10px;
    padding-right: 10px;
}

.mwa-modal-divider hr {
    border-top: 1px solid #D9DEDE;
}

.mwa-modal-subtitle {
    margin: auto;
    padding: 20px;
    color: #6E8286;
}

.mwa-modal-progress-badge {
    display: flex;
    background: #F7F8F8;
    height: 56px;
    min-width: 200px;
    margin: auto;
    padding-left: 20px;
    padding-right: 20px;
    border-radius: 24px;
    color: #A8B6B8;
    align-items: center;
}

.mwa-modal-progress-badge > div:first-child {
    margin-left: auto;
    margin-right: 20px;
}

.mwa-modal-progress-badge > div:nth-child(2) {
    margin-right: auto;
}

/* Smaller screens */
@media all and (max-width: 600px) {
    .mwa-modal-card {
        text-align: center;
    }
    .mwa-modal-card> div:nth-child(2) {
        flex-direction: column;
    }
    .mwa-modal-card> div:nth-child(2) > div:first-child {
        margin: auto;
    }
    .mwa-modal-card > div:nth-child(2) > div:nth-child(2) {
        margin: auto;
        flex: 2 auto;
    }
    .mwa-modal-card> div:nth-child(4) {
        flex-direction: column;
    }
    .mwa-modal-icon {
        display: none;
    }
    .mwa-modal-title {
        font-size: 1.5em;
    }
    .mwa-modal-qr-label {
        text-align: center;
    }
    .mwa-modal-qr-code-container {
        margin: auto;
    }
}

/* Spinner */
@keyframes spinLeft {
    0% {
        transform: rotate(20deg);
    }
    50% {
        transform: rotate(160deg);
    }
    100% {
        transform: rotate(20deg);
    }
}
@keyframes spinRight {
    0% {
        transform: rotate(160deg);
    }
    50% {
        transform: rotate(20deg);
    }
    100% {
        transform: rotate(160deg);
    }
}
@keyframes spin {
    0% {
        transform: rotate(0deg);
    }
    100% {
        transform: rotate(2520deg);
    }
}

.spinner {
    position: relative;
    width: 1.5em;
    height: 1.5em;
    margin: auto;
    animation: spin 10s linear infinite;
}
.spinner::before {
    content: "";
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    right: 0;
    /*border: 2px solid #D4D7DC;*/
    /*border-radius: 12px;*/
}
.right, .rightWrapper, .left, .leftWrapper {
    position: absolute;
    top: 0;
    overflow: hidden;
    width: .75em;
    height: 1.5em;
}
.left, .leftWrapper {
    left: 0;
}
.right {
    left: -12px;
}
.rightWrapper {
    right: 0;
}
.circle {
    border: .125em solid #A8B6B8;
    width: 1.25em; /* 1.5em - 2*0.125em border */
    height: 1.25em; /* 1.5em - 2*0.125em border */
    border-radius: 0.75em; /* 0.5*1.5em spinner size 8 */
}
.left {
    transform-origin: 100% 50%;
    animation: spinLeft 2.5s cubic-bezier(.2,0,.8,1) infinite;
}
.right {
    transform-origin: 100% 50%;
    animation: spinRight 2.5s cubic-bezier(.2,0,.8,1) infinite;
}
`;
