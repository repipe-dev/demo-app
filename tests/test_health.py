"""Tests run by both pipelines — CI parity depends on this file."""

from fastapi.testclient import TestClient

from src.app import APP_VERSION, app

client = TestClient(app)


def test_health_returns_ok():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["version"] == APP_VERSION
