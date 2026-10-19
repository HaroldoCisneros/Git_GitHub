"""GET /api/v1/salud: ¿la API está encendida? (no toca la base; sirve para probar el túnel)."""

from __future__ import annotations

from fastapi import APIRouter

from .. import __version__

router = APIRouter(tags=["salud"])


@router.get("/salud")
def salud() -> dict:
    return {"estado": "ok", "aplicacion": "AppSiguecPuraceramica", "version": __version__}
