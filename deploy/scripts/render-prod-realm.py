#!/usr/bin/env python3
"""Derives the production Keycloak realm import (deploy/keycloak/openjcockpit-realm.json) from the
development realm (infrastructure/keycloak/openjcockpit-realm.json), so roles, client scopes and theme stay
in one place while every development assumption is removed:

  * no users at all (no seeded accounts, no passwords); administrators are created per the runbook
  * the dashboard client accepts only the public URL (${OPENJCOCKPIT_PUBLIC_URL}, substituted by Keycloak at
    import time from the container environment), no localhost, no password grant (directAccessGrants off)
  * HTTPS required for all requests, brute-force protection, a password policy, short-lived tokens
  * login and admin events recorded (audit trail), without representation payloads

Usage:  deploy/scripts/render-prod-realm.py          # rewrite the file
        deploy/scripts/render-prod-realm.py --check  # CI: fail if the committed file is stale
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "infrastructure/keycloak/openjcockpit-realm.json"
TARGET = ROOT / "deploy/keycloak/openjcockpit-realm.json"
PUBLIC = "${OPENJCOCKPIT_PUBLIC_URL}"


def render() -> str:
    realm = json.loads(SOURCE.read_text())
    realm.pop("users", None)
    realm.update({
        "sslRequired": "all",
        "registrationAllowed": False,
        "resetPasswordAllowed": True,
        "editUsernameAllowed": False,
        "bruteForceProtected": True,
        "permanentLockout": False,
        "failureFactor": 5,
        "maxFailureWaitSeconds": 900,
        "passwordPolicy": "length(14) and notUsername and notEmail and passwordHistory(5)",
        "accessTokenLifespan": 300,
        "ssoSessionIdleTimeout": 1800,
        "ssoSessionMaxLifespan": 36000,
        "eventsEnabled": True,
        "eventsExpiration": 7776000,
        "enabledEventTypes": ["LOGIN", "LOGIN_ERROR", "LOGOUT", "LOGOUT_ERROR", "CODE_TO_TOKEN_ERROR",
                              "CLIENT_LOGIN", "CLIENT_LOGIN_ERROR", "REFRESH_TOKEN_ERROR", "UPDATE_PASSWORD",
                              "UPDATE_TOTP", "REMOVE_TOTP", "UPDATE_CREDENTIAL"],
        "adminEventsEnabled": True,
        "adminEventsDetailsEnabled": False,
    })
    for client in realm.get("clients", []):
        if client.get("clientId") != "openjcockpit":
            continue
        client.update({
            "rootUrl": PUBLIC,
            "adminUrl": PUBLIC,
            "redirectUris": [f"{PUBLIC}/*"],
            "webOrigins": [PUBLIC],
            "directAccessGrantsEnabled": False,
            "implicitFlowEnabled": False,
        })
        client.setdefault("attributes", {})["post.logout.redirect.uris"] = f"{PUBLIC}/*"
    text = json.dumps(realm, indent=2, ensure_ascii=False) + "\n"
    assert "localhost" not in text and "127.0.0.1" not in text, "development URL left in the production realm"
    assert '"credentials"' not in text, "credentials left in the production realm"
    return text


if __name__ == "__main__":
    text = render()
    if "--check" in sys.argv:
        if not TARGET.exists() or TARGET.read_text() != text:
            sys.exit(f"{TARGET.relative_to(ROOT)} is stale: run deploy/scripts/render-prod-realm.py")
        print("prod realm up to date")
    else:
        TARGET.parent.mkdir(parents=True, exist_ok=True)
        TARGET.write_text(text)
        print(f"wrote {TARGET.relative_to(ROOT)}")
