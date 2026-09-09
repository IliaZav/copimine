using CopiMineLauncher.Infrastructure.Runtime;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class InstanceOperationLockTests
{
    [Fact]
    public async Task Second_operation_waits_until_the_first_operation_releases_the_instance_lock()
    {
        using var temp = new TemporaryDirectory();
        await using var first = await InstanceOperationLock.AcquireAsync(temp.Path, CancellationToken.None);
        var secondTask = InstanceOperationLock.AcquireAsync(temp.Path, CancellationToken.None).AsTask();

        await Task.Delay(100);
        secondTask.IsCompleted.Should().BeFalse();

        await first.DisposeAsync();
        await using var second = await secondTask.WaitAsync(TimeSpan.FromSeconds(2));
        second.Should().NotBeNull();
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-instance-lock-").FullName;

        public string Path { get; }

        public void Dispose()
        {
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
