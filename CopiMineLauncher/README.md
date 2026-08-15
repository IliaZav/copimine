# CopiMine Launcher

The Task 1 scaffold targets .NET 10 and separates pure Core contracts, infrastructure boundaries, and a WPF application shell.

## Projects

- `src/CopiMineLauncher.Core`: target framework `net10.0` and shared launcher identity.
- `src/CopiMineLauncher.Infrastructure`: infrastructure dependencies and Core reference.
- `src/CopiMineLauncher.App`: WPF shell targeting `net10.0-windows`.
- `tests/*`: xUnit test projects. Test-only packages use `PrivateAssets="all"`.

## Verify

```powershell
dotnet restore CopiMineLauncher/CopiMineLauncher.sln
dotnet test CopiMineLauncher/CopiMineLauncher.sln -c Release --no-restore
```

The planned Velopack command-line tool is pinned to `vpk` 1.2.0; it is not installed or invoked by this scaffold task.
