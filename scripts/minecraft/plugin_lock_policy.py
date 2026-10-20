"""Shared policy for selecting Minecraft 26.3 plugin runtime candidates."""


def baseline_candidate_is_runtime_eligible(baseline: dict, minecraft_version: str) -> bool:
    """Return whether a baseline candidate belongs in the staged runtime.

    Historical rows remain available for rollback and audit. Only candidates
    explicitly opted in by the lock can be installed. External artifacts must
    declare the target game version (or an explicit approval); first-party
    builds must carry a matching API/approval field.
    """
    candidate = baseline.get("candidate")
    if candidate is None:
        return False
    if not isinstance(candidate, dict):
        raise ValueError("baseline candidate must be an object: " + str(baseline.get("pluginName", "unknown")))
    eligible = candidate.get("runtimeEligible", False)
    if type(eligible) is not bool:
        raise ValueError("baseline candidate runtimeEligible must be a boolean: " + str(baseline.get("pluginName", "unknown")))
    if not eligible:
        return False

    has_build = bool(candidate.get("buildArtifact"))
    has_url = bool(candidate.get("url"))
    if not has_build and not has_url:
        raise ValueError("runtime-eligible baseline candidate has no artifact source: " + str(baseline.get("pluginName", "unknown")))

    approved = candidate.get("approvedMinecraftVersion") == minecraft_version
    if has_build and (candidate.get("apiVersion") == minecraft_version or approved):
        return True
    versions = candidate.get("gameVersions")
    if has_url and ((isinstance(versions, list) and minecraft_version in versions) or approved):
        return True

    raise ValueError(
        "runtime-eligible candidate does not declare support for Minecraft "
        + str(minecraft_version) + ": " + str(baseline.get("pluginName", "unknown"))
    )
