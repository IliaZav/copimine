using Xunit;

// The runtime smoke tests intentionally receive one shared, seeded instance
// through environment variables. Serialise this assembly so CmlLib caches,
// launcher logs and CustomSkinLoader settings cannot mutate that fixture at
// the same time. The production code still has its own concurrent atomic-write
// regression test in Infrastructure.Tests.
[assembly: CollectionBehavior(DisableTestParallelization = true)]
