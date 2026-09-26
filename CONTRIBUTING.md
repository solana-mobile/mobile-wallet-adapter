# Contributing to Mobile Wallet Adapter

Thanks for your interest in improving Mobile Wallet Adapter! This document explains how we work so that your contribution lands quickly.

## Issues first, then pull requests

**Open an issue before you open a pull request.**

Every pull request costs a maintainer two things: reproducing the problem, and then verifying that the change fixes it. When the issue comes first we can do the reproduction once, confirm it is a bug in this SDK rather than in an integrating app, and agree on the shape of the fix before anyone writes code. That saves your time as well as ours.

The workflow is:

1. **Search existing issues.** Someone may already have reported it.
2. **File an issue** using the [bug report](https://github.com/solana-mobile/mobile-wallet-adapter/issues/new?template=bug_report.yml) or [feature request](https://github.com/solana-mobile/mobile-wallet-adapter/issues/new?template=feature_request.yml) template. Fill in every required field; our triage bot sends incomplete issues back for more detail.
3. **Wait for triage.** A maintainer (or the triage bot) confirms the reproduction and labels the issue `ready-for-engineering`. If you'd like to work on it, say so in a comment.
4. **Open a pull request** that references the issue with a closing keyword such as `Fixes #123`.

Pull requests that don't reference an accepted issue may be closed without review. We'd rather point you to the right place than let your work sit unreviewed, so we'll always leave a comment explaining why.

### Exceptions

You don't need an issue for trivial changes: typos, broken links, comment-only fixes, or small documentation corrections. Write "trivial" in the linked-issue field of the PR template.

### What belongs here

This repository is for the Mobile Wallet Adapter SDK itself. The issue tracker is **not** the right place for:

- **Usage and how-to questions.** Ask on [Solana Stack Exchange](https://solana.stackexchange.com/questions/ask) with the `solana-mobile` tag.
- **Seed Vault Wallet or Seeker device problems.** These are not maintained in this repo. Join the [Solana Mobile Discord](https://discord.gg/solanamobile).
- **Bugs in third-party apps** that integrate the SDK. We can only act on bugs reproducible with the SDK directly or with an example/test app in this repo.

## Pull request guidelines

- **Keep it scoped.** One issue per pull request. Refactors, formatting changes, and unrelated fixes go in their own PRs.
- **Describe how to verify it.** The PR template asks for test steps and which example/test app you used. This is what lets a maintainer confirm the fix without re-deriving your setup.
- **Add tests** where the change is testable.
- **Add a changeset** if you change a published npm package. Run `pnpm changeset` from the `js/` directory and commit the generated file under `js/.changeset/`. The `changeset:check` CI job fails without one.
- **Expect review turnaround to vary.** This is a small team. Linking an accepted issue is the best way to get to the front of the queue.

## Development setup

- **Android SDKs** live under `android/`. Open the directory in Android Studio or build with Gradle.
- **JavaScript and React Native packages** live under `js/`. Install with `pnpm install` and build with `pnpm build` from that directory.
- **Example apps** for each platform live under `examples/`. Reproductions and verification steps should use one of these where possible.

## Code of conduct

Be kind and constructive. We're all here to make mobile Solana apps better.
