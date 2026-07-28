from pathlib import Path

import pytest

from m4trust_mock_worker.config import Settings


def settings(environment="local", enabled=True, scenario="auto"):
    return Settings(enabled, environment, Path("contracts"), "localhost", 5672, "user", "pass", scenario, 1, 3)


@pytest.mark.parametrize(
    ("environment", "enabled", "message"),
    [("production", True, "forbidden"), ("staging", True, "forbidden"), ("local", False, "requires")],
)
def test_worker_fails_closed_when_not_explicitly_local_and_enabled(environment, enabled, message):
    with pytest.raises(RuntimeError, match=message):
        settings(environment, enabled).validate_startup()
