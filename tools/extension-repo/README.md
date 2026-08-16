# Build a trusted extension repository

This command-line interface creates and validates NewsHub's threshold-signed repository format. Follow [`docs/self-host-extension-repository.md`](../../docs/self-host-extension-repository.md) before using production keys.

Install the pinned dependency, then inspect commands:

```bash
python3 -m pip install -r requirements.txt
./newshub-extension-repo --help
```

The tool supports these commands:

- `init`: create one config and separated private keys
- `publish`: inspect APKs and write signed public metadata
- `validate`: verify a complete repository offline
- `fingerprints`: print root trust values for out-of-band comparison
- `rotate-root`: generate and cross-sign the next root version

Production mode requires `NEWSHUB_REPO_KEY_PASSWORD`. Never store that value in shell history, config, Git, or logs.
