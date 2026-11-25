"""The deliberately boring app that gets repiped."""

import json
import os

from fastapi import FastAPI

APP_VERSION = os.environ.get("APP_VERSION", "0.1.0")

app = FastAPI(title="demo-app", version=APP_VERSION)


@app.get("/health")
def health() -> dict:
    """Liveness probe payload; the pipeline's smoke test hits this."""
    return {"status": "ok", "version": APP_VERSION}
