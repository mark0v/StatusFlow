from __future__ import annotations

import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient
import pytest


APP_DIR = Path(__file__).resolve().parents[1]

if str(APP_DIR) not in sys.path:
    sys.path.insert(0, str(APP_DIR))


@pytest.fixture()
def client(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> TestClient:
    database_path = tmp_path / "statusflow-test.db"
    monkeypatch.setenv("DATABASE_URL", f"sqlite:///{database_path.as_posix()}")

    for module_name in [
        "app.main",
        "app.seed",
        "app.services",
        "app.models",
        "app.db",
    ]:
        sys.modules.pop(module_name, None)

    app_main = importlib.import_module("app.main")

    with TestClient(app_main.app) as test_client:
        yield test_client


def _customer_id(users: list[dict[str, str]]) -> str:
    return next(user["id"] for user in users if user["role"] == "customer")


def _operator_id(users: list[dict[str, str]]) -> str:
    return next(user["id"] for user in users if user["role"] == "operator")


def _login(client: TestClient, email: str, password: str) -> dict[str, str]:
    response = client.post(
        "/auth/login",
        json={"email": email, "password": password},
    )
    assert response.status_code == 200
    return response.json()


def _auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def test_healthcheck_returns_ok(client: TestClient) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_login_returns_access_token_for_seeded_user(client: TestClient) -> None:
    response = client.post(
        "/auth/login",
        json={"email": "operator@example.com", "password": "operator123"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["token_type"] == "bearer"
    assert body["access_token"]
    assert body["user"]["role"] == "operator"


def test_login_rejects_invalid_password(client: TestClient) -> None:
    response = client.post(
        "/auth/login",
        json={"email": "operator@example.com", "password": "wrong-pass"},
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid email or password."


def test_seeded_users_and_orders_are_available(client: TestClient) -> None:
    session = _login(client, "operator@example.com", "operator123")
    headers = _auth_headers(session["access_token"])
    users_response = client.get("/users", headers=headers)
    orders_response = client.get("/orders", headers=headers)

    assert users_response.status_code == 200
    assert orders_response.status_code == 200
    assert {user["role"] for user in users_response.json()} == {"customer", "operator"}
    assert {order["code"] for order in orders_response.json()} == {"SF-1001", "SF-1002", "SF-1003"}


def test_create_order_creates_new_order_with_initial_history(client: TestClient) -> None:
    session = _login(client, "operator@example.com", "operator123")
    headers = _auth_headers(session["access_token"])
    users = client.get("/users", headers=headers).json()
    payload = {
        "title": "Collect missing serial number",
        "description": "Customer forgot to attach the serial sticker photo.",
        "customer_id": _customer_id(users),
    }

    response = client.post("/orders", json=payload, headers=headers)

    assert response.status_code == 201
    body = response.json()
    assert body["code"] == "SF-1004"
    assert body["status"] == "new"
    assert body["customer"]["id"] == payload["customer_id"]
    assert body["history"][-1]["to_status"] == "new"
    assert body["history"][-1]["reason"] == "Order created."


def test_add_comment_appends_comment_and_keeps_author(client: TestClient) -> None:
    session = _login(client, "operator@example.com", "operator123")
    headers = _auth_headers(session["access_token"])
    users = client.get("/users", headers=headers).json()
    orders = client.get("/orders", headers=headers).json()
    payload = {
        "author_id": _operator_id(users),
        "body": "Please attach the purchase invoice.",
    }

    response = client.post(f"/orders/{orders[0]['id']}/comments", json=payload, headers=headers)

    assert response.status_code == 200
    body = response.json()
    assert body["comments"][-1]["body"] == payload["body"]
    assert body["comments"][-1]["author"]["id"] == payload["author_id"]


def test_valid_status_transition_records_history(client: TestClient) -> None:
    session = _login(client, "operator@example.com", "operator123")
    headers = _auth_headers(session["access_token"])
    users = client.get("/users", headers=headers).json()
    orders = client.get("/orders", headers=headers).json()
    new_order = next(order for order in orders if order["status"] == "new")
    payload = {
        "changed_by_id": _operator_id(users),
        "to_status": "in_review",
        "reason": "Operator started review.",
    }

    response = client.post(
        f"/orders/{new_order['id']}/status-transitions",
        json=payload,
        headers=headers,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "in_review"
    assert body["history"][-1]["from_status"] == "new"
    assert body["history"][-1]["to_status"] == "in_review"
    assert body["history"][-1]["reason"] == payload["reason"]


def test_invalid_status_transition_returns_422_with_allowed_transitions(client: TestClient) -> None:
    session = _login(client, "operator@example.com", "operator123")
    headers = _auth_headers(session["access_token"])
    users = client.get("/users", headers=headers).json()
    orders = client.get("/orders", headers=headers).json()
    approved_order = next(order for order in orders if order["status"] == "approved")
    payload = {
        "changed_by_id": _operator_id(users),
        "to_status": "new",
        "reason": "Trying to jump backward without review.",
    }

    response = client.post(
        f"/orders/{approved_order['id']}/status-transitions",
        json=payload,
        headers=headers,
    )

    assert response.status_code == 422
    body = response.json()["detail"]
    assert body["allowed_transitions"] == ["fulfilled", "cancelled"]
    assert "approved to new" in body["message"]


def test_orders_require_authentication(client: TestClient) -> None:
    response = client.get("/orders")

    assert response.status_code == 401
    assert response.json()["detail"] == "Authentication required."


def test_customer_cannot_transition_order_status(client: TestClient) -> None:
    customer_session = _login(client, "customer@example.com", "customer123")
    orders = client.get("/orders", headers=_auth_headers(customer_session["access_token"])).json()
    target = next(order for order in orders if order["status"] == "new")
    response = client.post(
        f"/orders/{target['id']}/status-transitions",
        json={
            "changed_by_id": _customer_id(client.get("/users", headers=_auth_headers(customer_session["access_token"])).json()),
            "to_status": "in_review",
            "reason": "Customer should not be allowed to do this.",
        },
        headers=_auth_headers(customer_session["access_token"]),
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "Operator role required."
