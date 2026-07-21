from dataclasses import dataclass
import os


ALLOWED_SCENARIOS = {"success", "decline", "timeout_then_late_success", "malformed_error", "not_found"}


@dataclass(frozen=True)
class Settings:
    enabled: bool
    environment: str
    host: str
    port: int
    scenarios: tuple[str, ...]
    max_request_bytes: int
    late_result_after_queries: int

    @classmethod
    def from_environment(cls):
        configured = os.getenv("M4TRUST_MOKA_EMULATOR_SCENARIOS", "success")
        scenarios = tuple(value.strip() for value in configured.split(",") if value.strip())
        return cls(
            enabled=os.getenv("M4TRUST_MOKA_EMULATOR_ENABLED", "false").lower() == "true",
            environment=os.getenv("APP_ENVIRONMENT", "local").lower(),
            host=os.getenv("M4TRUST_MOKA_EMULATOR_HOST", "127.0.0.1"),
            port=int(os.getenv("M4TRUST_MOKA_EMULATOR_PORT", "18081")),
            scenarios=scenarios,
            max_request_bytes=int(os.getenv("M4TRUST_MOKA_EMULATOR_MAX_REQUEST_BYTES", "8192")),
            late_result_after_queries=int(os.getenv("M4TRUST_MOKA_EMULATOR_LATE_RESULT_AFTER_QUERIES", "2")),
        )

    def validate_startup(self):
        if not self.enabled:
            raise RuntimeError("Moka emulator requires M4TRUST_MOKA_EMULATOR_ENABLED=true")
        if self.environment in {"production", "staging"}:
            raise RuntimeError("Moka emulator is forbidden in staging and production")
        if not self.scenarios or any(scenario not in ALLOWED_SCENARIOS for scenario in self.scenarios):
            raise RuntimeError("Moka emulator scenarios must be a non-empty supported startup sequence")
        if self.max_request_bytes <= 0 or self.max_request_bytes > 8192:
            raise RuntimeError("Moka emulator request limit must be between 1 and 8192 bytes")
        if self.late_result_after_queries < 1:
            raise RuntimeError("Moka emulator late-result query count must be positive")
