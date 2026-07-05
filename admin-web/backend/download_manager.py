from __future__ import annotations

from pathlib import Path
from typing import Any

from fastapi.responses import FileResponse

from .deploy_runtime import artifact_by_route, artifact_snapshot, project_root_from_backend


def artifact_metadata(bucket: str, filename: str) -> dict[str, Any]:
    project_root = project_root_from_backend(Path(__file__))
    artifact = artifact_by_route(project_root, bucket, filename)
    return artifact_snapshot(artifact)


def artifact_file_response(bucket: str, filename: str) -> FileResponse:
    metadata = artifact_metadata(bucket, filename)
    if not metadata.get("exists"):
        raise FileNotFoundError(f"Managed artifact is missing: {bucket}/{filename}")
    path = Path(str(metadata["path"]))
    headers = {
        "Cache-Control": "public, max-age=3600, immutable",
        "X-CopiMine-SHA1": str(metadata.get("sha1") or ""),
        "X-CopiMine-SHA256": str(metadata.get("sha256") or ""),
    }
    return FileResponse(
        path,
        filename=path.name,
        media_type=str(metadata.get("media_type") or "application/octet-stream"),
        headers=headers,
    )
