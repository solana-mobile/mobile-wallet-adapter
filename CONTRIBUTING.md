# Contributing to Mobile Wallet Adapter

Thanks for your interest in improving Mobile Wallet Adapter! This document explains how to contribute so that your report lands quickly.

## Contribute through issues

**Pull requests are limited to repository collaborators.** If you've found a bug or have an idea, open an issue instead.

Every change costs a maintainer two things: reproducing the problem, and then verifying that the change fixes it. A good issue lets us do the reproduction once, confirm it is a bug in this SDK rather than in an integrating app, and write the fix ourselves. A detailed report is the most valuable contribution you can make.

The workflow is:

1. **Search existing issues.** Someone may already have reported it.
2. **File an issue** using the [bug report](https://github.com/solana-mobile/mobile-wallet-adapter/issues/new?template=bug_report.yml) or [feature request](https://github.com/solana-mobile/mobile-wallet-adapter/issues/new?template=feature_request.yml) template. Fill in every required field. For bug reports, the triage bot sends incomplete reports back for more detail.
3. **Wait for triage.** For bug reports, a maintainer or the triage bot confirms the reproduction and labels the issue `ready-for-engineering`. Maintainers review feature requests separately.
4. **A maintainer picks it up.** The fix lands in a pull request that references your issue, and you'll be notified when it closes.

If you already know the fix, describe it in the issue: the root cause, the files involved, or a code snippet. That shortens the path from report to release.

## What belongs here

This repository is for the Mobile Wallet Adapter SDK itself. The issue tracker is **not** the right place for:

- **Bugs in third-party apps** that integrate the SDK. We can only act on bugs reproducible with the SDK directly or with an example/test app in this repo.
- **Seed Vault Wallet or Seeker device problems.** These are not maintained in this repo. Join the [Solana Mobile Discord](https://discord.gg/solanamobile).
- **Usage and how-to questions.** Ask on [Solana Stack Exchange](https://solana.stackexchange.com/questions/ask) with the `solana-mobile` tag.

## Writing a good issue

- **Include a reproduction.** Use one of the example apps under `examples/` where possible, and list the exact steps.
- **Keep it scoped.** One problem per issue. Related but separate problems go in their own issues.
- **Share versions.** Include the SDK package versions, the wallet app, and the device or emulator you tested on.

## Code of conduct

Be kind and constructive. We're all here to make mobile Solana apps better.
