FROM python:3.13.5-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/src \
    M4TRUST_CONTRACTS_DIR=/app/contracts

WORKDIR /app

COPY tools/mock-ai-worker/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir --requirement /app/requirements.txt \
    && addgroup --system m4trust \
    && adduser --system --ingroup m4trust --home /nonexistent m4trust

COPY contracts /app/contracts
COPY tools/mock-ai-worker/src /app/src

USER m4trust

CMD ["python", "-m", "m4trust_mock_worker"]
