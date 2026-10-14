Debug-only signing key (PKCS12, base64 in debug-keystore.b64). Public on purpose: it lets every debug build, from CI or a laptop, update the one before it. Never sign a release with it.
