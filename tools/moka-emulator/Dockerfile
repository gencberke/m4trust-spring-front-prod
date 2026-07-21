FROM python:3.13.5-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/src

WORKDIR /app

COPY tools/moka-emulator/src /app/src

USER 65534:65534

CMD ["python", "-m", "m4trust_moka_emulator"]
