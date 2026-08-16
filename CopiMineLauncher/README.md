# CopiMine Launcher

The launcher targets .NET 10 and separates pure Core contracts, infrastructure boundaries, and a WPF application shell.

## Projects

- `src/CopiMineLauncher.Core`: target framework `net10.0` and shared launcher identity.
- `src/CopiMineLauncher.Infrastructure`: infrastructure dependencies and Core reference.
- `src/CopiMineLauncher.App`: WPF shell targeting `net10.0-windows`.
- `tests/*`: xUnit test projects. Test-only packages use `PrivateAssets="all"`.

## Скины и плащи

Кнопка `Скины` открывает встроенный менеджер косметики:

- публичный каталог Ely.by загружается постранично через его JSON-выдачу; чувствительные теги скрыты по умолчанию;
- профиль по нику запрашивается через Mojang profile/session API с резервным запросом Ely.by;
- отдельная вкладка плащей запрашивает доступные варианты по нику из публичного capes.dev API и не меняет аккаунт;
- PNG/JPG/BMP/GIF можно импортировать из файла; изображение нормализуется в PNG и проверяется по Minecraft-размерам;
- локальные текстуры применяются в `CustomSkinLoader/LocalSkin/{skins|capes}/<ник>.png`;
- предпросмотр работает локально через поставляемый `skinview3d`: мышиное вращение, автоповорот, фоны, Steve/Slim и ходьба/покой/бег/мах рукой/приседание/полёт/плавание.

Приватные данные, пароли и учётные токены в менеджер не передаются. Каталог и внешние профили используются только для загрузки публичных текстур; установка в Minecraft остаётся локальной. WebView2 устанавливается только при необходимости через официальный Microsoft Evergreen Bootstrapper, который входит в установщик.

## Verify

```powershell
dotnet restore CopiMineLauncher/CopiMineLauncher.sln
dotnet test CopiMineLauncher/CopiMineLauncher.sln -c Release --no-restore
```

Release packaging uses the pinned Velopack CLI 1.2.0 from
`artifacts/tools/vpk/vpk.exe`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build_copimine_launcher.ps1 -Configuration Release -InstanceReleaseRoot artifacts/launcher/Release/instance-current
```

The installer keeps the mutable Minecraft instance outside the replaceable
Velopack application directory. For an install root such as
`D:\Games\CopiMine\Launcher`, the game data is stored in
`D:\Games\CopiMine\Minecraft` so launcher updates do not empty the game
folder or remove user files.
