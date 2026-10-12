# Release signing key

`openmacro-release.p12.enc` is the app's release signing key (PKCS#12, alias `openmacro`),
encrypted with AES-256 (OpenSSL, PBKDF2, 600,000 iterations). It is useless without the password,
which is kept only as the `OM_SIGNING_PASSWORD` repository secret and by the maintainer.

Every release must be signed with this same key; Android only installs an update when the
signature matches the installed app. The Release workflow unlocks it at build time and checks
that each APK carries this certificate:

```
SHA-256: 0E:A5:81:37:06:0A:5F:A5:66:C3:39:77:E3:15:A0:96:92:77:5C:85:2A:C8:B0:7A:F7:0E:58:10:FE:25:AC:E3
```

To unlock it locally (for example to sign a build by hand):

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 \
  -in signing/openmacro-release.p12.enc -out release.p12
```

Never commit the decrypted `release.p12`; `.gitignore` blocks `*.p12` files.
