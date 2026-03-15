# Test TLS Credentials

The certificate and private key files in this directory (`*.crt`, `*.key`, `*.pem`, `*.jks`)
are **non-production, self-signed test credentials** generated solely for automated integration
tests. They are intentionally committed to the repository so that tests are hermetic and
reproducible without network access.

**These files MUST NOT be used outside of the test environment.**
They carry no real authority and provide no real security.

If you need to regenerate them, see the instructions in `jks_keystore_truststore/README.md`.
