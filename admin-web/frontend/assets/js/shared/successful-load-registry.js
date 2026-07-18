export function createSuccessfulLoadRegistry() {
  const loaded = new Set();
  const pending = new Map();

  function normalizeKey(key) {
    return String(key || "").trim();
  }

  return {
    has(key) {
      return loaded.has(normalizeKey(key));
    },
    run(key, load) {
      const normalizedKey = normalizeKey(key);
      if (loaded.has(normalizedKey)) return Promise.resolve();
      if (pending.has(normalizedKey)) return pending.get(normalizedKey);

      const task = Promise.resolve()
        .then(load)
        .then((result) => {
          loaded.add(normalizedKey);
          return result;
        })
        .finally(() => pending.delete(normalizedKey));
      pending.set(normalizedKey, task);
      return task;
    },
  };
}
