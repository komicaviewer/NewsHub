# Third-party extension documentation plan

This plan keeps third-party development, repository publishing, trust, reference, and troubleshooting as separate reader tasks.

- **Goal**: let an Android developer build, sign, publish, install, and update one protocol v2 extension without undocumented steps
- **Audience**: third-party Source developers and repository operators
- **Tutorial**: build and test an isolated extension from the starter
- **How-to**: create and host signed metadata, then rotate keys
- **Reference**: define the complete APK, service, runtime, policy, and repository contract
- **Troubleshooting**: map failure messages to local checks
- **Published API pin**: protocol v2 resolves from JitPack commit `6a94c4879ebbf052007dc6fa6374deade2428e57`

Validation requires a clean starter build, publisher tests, offline repository validation, and disposable-emulator installation.
