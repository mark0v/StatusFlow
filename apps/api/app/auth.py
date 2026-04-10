from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import secrets
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.db import get_db
from app.domain import UserRole
from app.models import User


TOKEN_VERSION = "v1"
TOKEN_TTL_HOURS = 12
security = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class AuthenticatedUser:
    id: str
    email: str
    name: str
    role: UserRole


def _auth_secret() -> str:
    return os.getenv("AUTH_SECRET", "statusflow-dev-secret")


def hash_password(password: str) -> str:
    salt = hashlib.sha256(_auth_secret().encode("utf-8")).digest()
    digest = hashlib.pbkdf2_hmac(
        "sha256",
        password.encode("utf-8"),
        salt,
        120_000,
    )
    return base64.urlsafe_b64encode(digest).decode("utf-8")


def verify_password(password: str, password_hash: str) -> bool:
    return secrets.compare_digest(hash_password(password), password_hash)


def create_access_token(user: User) -> str:
    issued_at = datetime.now(UTC)
    expires_at = issued_at + timedelta(hours=TOKEN_TTL_HOURS)
    payload = {
        "sub": user.id,
        "role": user.role.value,
        "email": user.email,
        "exp": int(expires_at.timestamp()),
        "iat": int(issued_at.timestamp()),
        "ver": TOKEN_VERSION,
    }
    payload_json = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
    payload_b64 = base64.urlsafe_b64encode(payload_json).decode("utf-8").rstrip("=")
    signature = hmac.new(
        _auth_secret().encode("utf-8"),
        payload_b64.encode("utf-8"),
        hashlib.sha256,
    ).digest()
    signature_b64 = base64.urlsafe_b64encode(signature).decode("utf-8").rstrip("=")
    return f"{payload_b64}.{signature_b64}"


def _decode_token(token: str) -> dict[str, object]:
    try:
        payload_b64, signature_b64 = token.split(".", 1)
    except ValueError as error:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Malformed access token.",
        ) from error

    expected_signature = hmac.new(
        _auth_secret().encode("utf-8"),
        payload_b64.encode("utf-8"),
        hashlib.sha256,
    ).digest()
    actual_signature = base64.urlsafe_b64decode(_pad_b64(signature_b64))

    if not secrets.compare_digest(expected_signature, actual_signature):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid access token signature.",
        )

    try:
        payload_bytes = base64.urlsafe_b64decode(_pad_b64(payload_b64))
        payload = json.loads(payload_bytes.decode("utf-8"))
    except Exception as error:  # noqa: BLE001
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid access token payload.",
        ) from error

    exp = payload.get("exp")
    if not isinstance(exp, int) or exp < int(datetime.now(UTC).timestamp()):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Access token expired.",
        )

    if payload.get("ver") != TOKEN_VERSION:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Unsupported access token version.",
        )

    return payload


def _pad_b64(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return f"{value}{padding}".encode("utf-8")


def authenticate_user(session: Session, email: str, password: str) -> User | None:
    user = session.query(User).filter(User.email == email.lower().strip()).one_or_none()
    if user is None or not verify_password(password, user.password_hash):
        return None
    return user


def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(security),
    db: Session = Depends(get_db),
) -> AuthenticatedUser:
    if credentials is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required.",
        )

    payload = _decode_token(credentials.credentials)
    subject = payload.get("sub")
    if not isinstance(subject, str):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid access token subject.",
        )

    user = db.get(User, subject)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authenticated user no longer exists.",
        )

    return AuthenticatedUser(
        id=user.id,
        email=user.email,
        name=user.name,
        role=user.role,
    )


def require_operator(user: AuthenticatedUser = Depends(get_current_user)) -> AuthenticatedUser:
    if user.role != UserRole.OPERATOR:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Operator role required.",
        )
    return user
