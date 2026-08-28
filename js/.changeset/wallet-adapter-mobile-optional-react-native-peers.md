---
'@solana-mobile/wallet-adapter-mobile': minor
---

Declare `react-native` and `@react-native-async-storage/async-storage` as optional peer dependencies, moving async-storage out of `optionalDependencies` and widening its accepted range to `^1.17.7 || ^2.0.0`, and load async-storage lazily.

`react-native` was a required peer dependency and async-storage sat in `optionalDependencies`, both of which package managers install by default, so browser-only consumers were pulling the entire React Native toolchain — including `metro` and the transitive advisories it carries — into `node_modules`. Both packages are reached only through the `react-native` export condition, so which code runs where is unchanged; a browser-only install no longer contains `react-native`, `metro`, or async-storage.

**Action required for React Native consumers that use the default authorization result cache.** If you call `createDefaultAuthorizationResultCache()`, add `@react-native-async-storage/async-storage` to your own app dependencies — it is no longer installed transitively. The release also adds runtime validation: async-storage is now resolved lazily, on first use, and `createDefaultAuthorizationResultCache()` throws a descriptive error when it is missing or unusable (such as when its native module has not been linked) instead of failing silently into an inert cache. Apps that pass their own `authorizationResultCache` never load async-storage and do not need it installed; the lazy `require` sits inside a `try` block, which Metro's default `allowOptionalDependencies` configuration treats as an optional dependency, so their builds keep working without it.
