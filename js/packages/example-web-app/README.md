# Example Mobile Web dApp

This demonstrates how you can use the mobile wallet adapter in a web app.

https://user-images.githubusercontent.com/13243/174901783-908885d5-08ba-49c4-9ef4-3871affc528f.mp4

## Features

-   Authorize the web app for use with a native mobile wallet app.
-   Record a message of your choosing on-chain, using the Memo program.
-   Sign a message of your choosing.
-   Request an airdrop of SOL on devnet.

## Prerequisites

To make it convenient to load the example app on your mobile phone we suggest creating a reverse proxy to your local machine on which the example app's websever is running. We suggest using ngrok, which you can set up by following [these instructions](https://ngrok.com/docs/getting-started).

## Quick Start

1. Build and host the app with `yarn && yarn build && yarn start`.
2. Once the app is listening for connections on `localhost` open a remote tunnel to it in a separate terminal, using `yarn tunnel:start`
3. Load the `https` tunnel URL on the Android mobile device that has a native wallet app installed.
