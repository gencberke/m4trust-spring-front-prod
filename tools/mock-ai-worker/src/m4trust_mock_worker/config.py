from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    enabled: bool
    environment: str
    contracts_dir: Path
    rabbitmq_host: str
    rabbitmq_port: int
    rabbitmq_user: str
    rabbitmq_password: str
    scenario: str
    download_timeout_seconds: float
    download_max_attempts: int
    download_host_override: str | None = None

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            enabled=_boolean("M4TRUST_MOCK_AI_ENABLED", False),
            environment=os.getenv("APP_ENVIRONMENT", "local").strip().lower(),
            contracts_dir=Path(os.getenv("M4TRUST_CONTRACTS_DIR", "contracts")).resolve(),
            rabbitmq_host=os.getenv("M4TRUST_RABBITMQ_HOST", "localhost"),
            rabbitmq_port=int(os.getenv("M4TRUST_RABBITMQ_PORT", "5672")),
            rabbitmq_user=os.getenv("M4TRUST_RABBITMQ_USER", "m4trust_local"),
            rabbitmq_password=os.getenv("M4TRUST_RABBITMQ_PASSWORD", "m4trust_local_password"),
            scenario=os.getenv("M4TRUST_MOCK_AI_SCENARIO", "auto").strip().lower(),
            download_timeout_seconds=float(os.getenv("M4TRUST_MOCK_AI_DOWNLOAD_TIMEOUT_SECONDS", "10")),
            download_max_attempts=int(os.getenv("M4TRUST_MOCK_AI_DOWNLOAD_MAX_ATTEMPTS", "3")),
            download_host_override=os.getenv("M4TRUST_MOCK_AI_DOWNLOAD_HOST_OVERRIDE") or None,
        )

    def validate_startup(self) -> None:
        if not self.enabled:
            raise RuntimeError("Mock AI Worker requires M4TRUST_MOCK_AI_ENABLED=true")
        if self.environment in {"prod", "production"}:
            raise RuntimeError("Mock AI Worker is forbidden in production")
        if self.scenario not in {"auto", "success", "retryable_failure", "duplicate"}:
            raise RuntimeError("Unsupported mock scenario configuration")
        if self.download_max_attempts < 1 or self.download_max_attempts > 10:
            raise RuntimeError("Download attempts must be between 1 and 10")


def _boolean(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    return default if raw is None else raw.strip().lower() in {"1", "true", "yes", "on"}
