import "./js/bootstrap.js";

// Compatibility manifest for cabinet validators and route-aware tooling.
// The actual anticheat implementation is loaded through the legacy cabinet runtime.
export const CABINET_FEATURES = Object.freeze([
  ["anticheat", "Античит", "GrimAC и нарушения", "А"],
]);

export async function loadAnticheat() {
  const legacy = await import("./js/legacy/app-legacy.js");
  return legacy;
}

export const ANTICHEAT_ENDPOINT = "/api/anticheat/status";
export const ANTICHEAT_EVENTS_TABLE_ID = "anticheat-events";
