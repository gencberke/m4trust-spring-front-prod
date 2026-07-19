from pathlib import Path

import pytest

from m4trust_mock_worker.config import Settings


def settings(environment="local", enabled=True):
    return Settings(enabled, environment, Path("contracts"), "localhost", 5672, "user", "pass", "auto", 1, 3)


def test_worker_refuses_production():
    with pytest.raises(RuntimeError, match="forbidden"):
        settings("production").validate_startup()


def test_worker_requires_explicit_enablement():
    with pytest.raises(RuntimeError, match="requires"):
        settings(enabled=False).validate_startup()
