#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -n "${LENSGUARD_KEYSTORE:-}" ]; then
    exit 0
fi
mkdir -p signing
if [ ! -f signing/development.p12 ]; then
    keytool -genkeypair -noprompt -keystore signing/development.p12 -storetype PKCS12 \
        -storepass lensguard-development -keypass lensguard-development \
        -alias lensguard -keyalg RSA -keysize 3072 -validity 3650 \
        -dname 'CN=LensGuard Evaluation, O=LensGuard'
fi
printf '%s\n' 'Evaluation signing: generated private local identity; CI builds use a fresh identity.'
