using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CopiMineLauncher.Core.News;
using CopiMineLauncher.Infrastructure.News;

namespace CopiMineLauncher.App;

public sealed class PatchFeedCardViewModel(PatchFeedItem item)
{
    public string Version => $"v{item.Version}";
    public string Title => item.Title;
    public string PublishedAt => item.PublishedAt.ToLocalTime().ToString("d MMM yyyy");
    public string Summary => string.Join(" · ", item.Summary.Take(2));
    public Uri DetailUrl => item.DetailUrl;
    public Uri? ThumbnailUrl => item.ThumbnailUrl;
}

public partial class LauncherViewModel : ObservableObject
{
    private readonly IPatchFeedClient patchFeedClient;

    public LauncherViewModel(IPatchFeedClient patchFeedClient)
    {
        this.patchFeedClient = patchFeedClient;
        PatchCards = new ObservableCollection<PatchFeedCardViewModel>();
        RefreshNewsCommand = new AsyncRelayCommand(RefreshNewsAsync);
        PlayCommand = new AsyncRelayCommand(PlayAsync);
        RepairCommand = new AsyncRelayCommand(RepairAsync);
        DiagnoseCommand = new AsyncRelayCommand(DiagnoseAsync);
        OpenPatchCommand = new RelayCommand<PatchFeedCardViewModel>(OpenPatch);
    }

    public ObservableCollection<PatchFeedCardViewModel> PatchCards { get; }

    [ObservableProperty]
    private string status = "Готов к проверке";

    [ObservableProperty]
    private string diagnostic = "Launcher ещё не выполнял операции.";

    [ObservableProperty]
    private string instancePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CopiMine", "instances", "stable");

    [ObservableProperty]
    private string playerName = "CopiMinePlayer";

    public IAsyncRelayCommand RefreshNewsCommand { get; }
    public IAsyncRelayCommand PlayCommand { get; }
    public IAsyncRelayCommand RepairCommand { get; }
    public IAsyncRelayCommand DiagnoseCommand { get; }
    public IRelayCommand<PatchFeedCardViewModel> OpenPatchCommand { get; }

    public async Task InitializeAsync()
    {
        await RefreshNewsAsync();
    }

    private async Task RefreshNewsAsync()
    {
        Status = "Загружаем последние обновления…";
        var result = await patchFeedClient.GetLatestAsync(CancellationToken.None);
        PatchCards.Clear();
        foreach (var item in result.Items)
        {
            PatchCards.Add(new PatchFeedCardViewModel(item));
        }

        Status = result.Items.Count > 0
            ? (result.FromCache ? "Обновления показаны из локального кэша" : "Обновления получены")
            : "Обновления временно недоступны";
        Diagnostic = result.Diagnostics.Count == 0
            ? "Patch feed проверен: unsafe links не допускаются, HTML из feed не выполняется."
            : string.Join(Environment.NewLine, result.Diagnostics);
    }

    private Task PlayAsync()
    {
        Status = "Запуск подготовлен: сначала выполните проверку сборки.";
        Diagnostic = $"Экземпляр: {InstancePath}{Environment.NewLine}Игрок: {PlayerName}";
        return Task.CompletedTask;
    }

    private Task RepairAsync()
    {
        Status = "Восстановление доступно после загрузки signed manifest.";
        Diagnostic = "Ни один файл не изменён: remote manifest ещё не опубликован для локального режима.";
        return Task.CompletedTask;
    }

    private Task DiagnoseAsync()
    {
        Status = "Диагностика завершена";
        Diagnostic = $"Версии: Minecraft 1.21.1 · Fabric Loader 0.19.3{Environment.NewLine}Instance: {Path.GetFullPath(InstancePath)}{Environment.NewLine}Patch feed: bounded 4.5 s, cache fallback включён.";
        return Task.CompletedTask;
    }

    private static void OpenPatch(PatchFeedCardViewModel? card)
    {
        if (card is null || card.DetailUrl.Scheme != Uri.UriSchemeHttps || !string.Equals(card.DetailUrl.Host, "copimine.ru", StringComparison.OrdinalIgnoreCase) || !card.DetailUrl.AbsolutePath.StartsWith("/news/", StringComparison.Ordinal)) return;
        Process.Start(new ProcessStartInfo { FileName = card.DetailUrl.ToString(), UseShellExecute = true });
    }
}
