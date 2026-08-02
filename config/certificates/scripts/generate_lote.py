#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0


import jwt
import time
import json
import base64
import datetime
import subprocess

def get_clcert_base64(p12_path, password):
    cmd = f"openssl pkcs12 -in {p12_path} -passin pass:{password} -nokeys -clcerts | openssl x509 -outform DER | base64 -w0"
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def get_cacert_base64(p12_path, password):
    cmd = f"openssl pkcs12 -in {p12_path} -passin pass:{password} -nokeys -cacerts | openssl x509 -outform DER | base64 -w0"
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def get_pem_cert_base64(pem_path):
    cmd = f"openssl x509 -in {pem_path} -outform DER | base64 -w0"
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def main():
    wallet_clcert_b64 = get_clcert_base64('../wallet-provider/wallet_provider.p12', 'pass1234')
    wallet_cacert_b64 = get_cacert_base64('../wallet-provider/wallet_provider.p12', 'pass1234')
    issuer_clcert_b64 = get_clcert_base64('../issuer/pid_issuer.p12', 'pass1234')
    issuer_cacert_b64 = get_cacert_base64('../issuer/pid_issuer.p12', 'pass1234')
    
    trust_source_cert_b64 = get_pem_cert_base64('../trust-list-signer/trust_source_cert.pem')
    
    with open('../trust-list-signer/trust_source_key.pem', 'r') as f:
        private_key = f.read()

    now = datetime.datetime.now(datetime.timezone.utc)
    next_update = now + datetime.timedelta(days=365)
    
    payload = {
      "LoTE": {
        "ListAndSchemeInformation": {
          "LoTEVersionIdentifier": 1,
          "LoTESequenceNumber": 1,
          "LoTEType": "http://uri.etsi.org/19602/LoTEType/wallet-providers",
          "SchemeOperatorName": [{"lang": "en", "value": "DIGG"}],
          "SchemeName": [{"lang": "en", "value": "Local LoTE"}],
          "SchemeTerritory": "SE",
          "ListIssueDateTime": now.isoformat().replace('+00:00', 'Z'),
          "NextUpdate": next_update.isoformat().replace('+00:00', 'Z')
        },
        "TrustedEntitiesList": [
          {
            "TrustedEntityInformation": {
              "TEName": [{"lang": "en", "value": "Local Wallet Provider"}],
              "TEAddress": {
                 "TEPostalAddress": [{"lang": "en", "StreetAddress": "Local", "Country": "SE"}],
                 "TEElectronicAddress": [{"lang": "en", "uriValue": "http://localhost"}]
              },
              "TEInformationURI": [{"lang": "en", "uriValue": "http://localhost"}]
            },
            "TrustedEntityServices": [
              {
                "ServiceInformation": {
                  "ServiceName": [{"lang": "en", "value": "Local Wallet Issuance"}],
                  "ServiceDigitalIdentity": {
                    "X509Certificates": [
                      {"val": wallet_clcert_b64},
                      {"val": wallet_cacert_b64}
                    ]
                  },
                  "ServiceTypeIdentifier": "http://uri.etsi.org/19602/SvcType/WalletSolution/Issuance",
                  "ServiceStatus": "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted",
                  "StatusStartingTime": now.isoformat().replace('+00:00', 'Z')
                }
              }
            ]
          },
          {
            "TrustedEntityInformation": {
              "TEName": [{"lang": "en", "value": "Local PID Issuer"}],
              "TEAddress": {
                 "TEPostalAddress": [{"lang": "en", "StreetAddress": "Local", "Country": "SE"}],
                 "TEElectronicAddress": [{"lang": "en", "uriValue": "http://localhost"}]
              },
              "TEInformationURI": [{"lang": "en", "uriValue": "http://localhost"}]
            },
            "TrustedEntityServices": [
              {
                "ServiceInformation": {
                  "ServiceName": [{"lang": "en", "value": "Local PID Issuance"}],
                  "ServiceDigitalIdentity": {
                    "X509Certificates": [
                      {"val": issuer_clcert_b64},
                      {"val": issuer_cacert_b64}
                    ]
                  },
                  "ServiceTypeIdentifier": "http://uri.etsi.org/19602/SvcType/PID/Issuance",
                  "ServiceStatus": "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted",
                  "StatusStartingTime": now.isoformat().replace('+00:00', 'Z')
                }
              }
            ]
          }
        ]
      }
    }
    
    headers = {
        "x5c": [trust_source_cert_b64]
    }
    
    token = jwt.encode(payload, private_key, algorithm="ES256", headers=headers)
    
    with open('../../trust-source/signed/wallet-providers.json', 'w') as f:
        f.write(token)
    print("Successfully wrote LoTE to config/trust-source/signed/wallet-providers.json")

if __name__ == '__main__':
    main()
