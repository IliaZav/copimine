namespace CopiMineLauncher.App;

public readonly record struct SkinSelectionActivation(long Version, string ItemId);

public sealed class SkinSelectionActivationGate
{
    private long version;

    public SkinSelectionActivation Begin(string itemId)
    {
        if (string.IsNullOrWhiteSpace(itemId)) throw new ArgumentException("Идентификатор скина обязателен.", nameof(itemId));
        return new(Interlocked.Increment(ref version), itemId);
    }

    public bool IsCurrent(SkinSelectionActivation activation) =>
        activation.Version == Volatile.Read(ref version);
}
