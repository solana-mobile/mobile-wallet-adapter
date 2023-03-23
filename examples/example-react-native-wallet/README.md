# Example React Native Wallet

This demonstrates how you can use the mobile wallet adapter wallet lib in a React Native Wallet app.

https://user-images.githubusercontent.com/13243/180274812-9cff2bdf-01d2-44fe-b094-52d3f9b22c4e.mp4

## Prerequisites

1. Set up the Android development environment by following the [environment setup instructions](https://reactnative.dev/docs/environment-setup) for your OS.
2. Install at least one mobile wallet adapter compliant dapp app on your device/simulator. You can build and install [`fakedapp`](../../android/fakedapp/) for testing purposes.

## Quick Start

### Android

1. Install dependencies and build the client libraries locally with `yarn`.
2. Start the React Native packager, build the application, and start the simulator with `yarn android`.

## Development

After making changes to any of the client libraries in `js/packages/` you will need to re-run `yarn` in this directory. This will rebuild the client libraries and copy their build artifacts into the `node_modules/` folder of this example app.