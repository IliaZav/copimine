from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github" / "workflows" / "ci.yml"


def test_java_release_job_prepares_python_and_asserts_resource_pack_artifact() -> None:
    text = WORKFLOW.read_text(encoding="utf-8")
    java_job = text.split("\n  java-plugins:\n", 1)[1]

    setup = "uses: actions/setup-python@v5"
    install = "python -m pip install --disable-pip-version-check 'Pillow==12.3.0'"
    build = "Build resource pack release artifact"
    archive_assertion = "Resource pack archive was not created"
    sidecar_assertion = "Resource-pack digest sidecar was not created"

    assert setup in java_job
    assert install in java_job
    assert build in java_job
    assert archive_assertion in java_job
    assert sidecar_assertion in java_job
    assert java_job.index(setup) < java_job.index(build)
    assert java_job.index(install) < java_job.index(build)
    assert java_job.index(archive_assertion) < java_job.index("Run all source and release-artifact validators")
