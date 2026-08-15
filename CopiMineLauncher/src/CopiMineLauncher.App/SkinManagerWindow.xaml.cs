using System.IO;
using System.Diagnostics;
using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Media.Imaging;
using CopiMineLauncher.Infrastructure.Skins;
using Microsoft.Web.WebView2.Core;
using Microsoft.Win32;

namespace CopiMineLauncher.App;

public partial class SkinManagerWindow : Window
{
    private readonly string instancePath;
    private readonly string launcherDataRoot;
    private readonly string previewRoot;
    private readonly HttpClient httpClient;
    private readonly ElyByCatalogClient catalogClient;
    private readonly CapesDevClient capesClient;
    private readonly PlayerCosmeticsClient playerCosmeticsClient;
    private readonly LocalCosmeticsStore localStore;
    private readonly CancellationTokenSource lifetime = new();
    private readonly SemaphoreSlim previewGate = new(1, 1);
    private readonly List<CatalogItemViewModel> catalogItems = [];
    private readonly List<CapeCatalogItemViewModel> capeItems = [];
    private bool initialized;
    private bool previewReady;
    private bool hasNextPage;
    private int currentPage = 1;
    private string? selectedSkinPath;
    private string? selectedCapePath;

    public SkinManagerWindow(string instancePath, string playerName, string launcherDataRoot)
    {
        InitializeComponent();
        this.instancePath = Path.GetFullPath(instancePath ?? throw new ArgumentNullException(nameof(instancePath)));
        this.launcherDataRoot = Path.GetFullPath(launcherDataRoot ?? throw new ArgumentNullException(nameof(launcherDataRoot)));
        previewRoot = Path.Combine(this.launcherDataRoot, "cosmetics", "preview");
        httpClient = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
        catalogClient = new ElyByCatalogClient(httpClient);
        capesClient = new CapesDevClient(httpClient);
        playerCosmeticsClient = new PlayerCosmeticsClient(httpClient);
        localStore = new LocalCosmeticsStore(this.instancePath, this.launcherDataRoot);
        PlayerNameTextBox.Text = playerName;
        ModelComboBox.SelectedIndex = 0;
        AnimationComboBox.SelectedIndex = 0;
        BackgroundComboBox.SelectedIndex = 0;
        Loaded += OnLoaded;
        Closed += OnClosed;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        Loaded -= OnLoaded;
        try
        {
            await InitializePreviewAsync();
        }
        catch (Exception exception)
        {
            SetStatus($"Предпросмотр не запущен: {exception.Message}");
        }
        finally
        {
            initialized = true;
        }

        await LoadCatalogAsync(reset: true);
        try
        {
            await LoadInstalledTexturesAsync();
        }
        catch (Exception exception)
        {
            SetStatus($"Локальные текстуры не загружены: {exception.Message}");
        }
    }

    private async Task InitializePreviewAsync()
    {
        Directory.CreateDirectory(previewRoot);
        CopyPreviewAsset("skin-preview.html");
        CopyPreviewAsset("skinview3d.bundle.js");
        await WritePreviewConfigAsync();

        await EnsureWebView2RuntimeAsync();
        PreviewView.CoreWebView2.Settings.AreDevToolsEnabled = false;
        PreviewView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
        PreviewView.CoreWebView2.WebMessageReceived += PreviewMessageReceived;
        PreviewView.CoreWebView2.SetVirtualHostNameToFolderMapping(
            "copimine.local",
            previewRoot,
            CoreWebView2HostResourceAccessKind.Allow);

        var navigation = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        void NavigationCompleted(object? _, CoreWebView2NavigationCompletedEventArgs args) => navigation.TrySetResult(args.IsSuccess);
        PreviewView.NavigationCompleted += NavigationCompleted;
        PreviewView.Source = new Uri("https://copimine.local/skin-preview.html", UriKind.Absolute);
        _ = await navigation.Task.WaitAsync(TimeSpan.FromSeconds(15), lifetime.Token);
        PreviewView.NavigationCompleted -= NavigationCompleted;
        previewReady = true;
        await SendPreviewAsync();
    }

    private async Task EnsureWebView2RuntimeAsync()
    {
        try
        {
            await PreviewView.EnsureCoreWebView2Async();
            return;
        }
        catch (Exception firstException)
        {
            var bootstrapper = Path.Combine(AppContext.BaseDirectory, "Assets", "WebView2", "MicrosoftEdgeWebView2Setup.exe");
            if (!File.Exists(bootstrapper)) throw;

            SetStatus("Устанавливаем компонент предпросмотра Microsoft WebView2…");
            using var process = Process.Start(new ProcessStartInfo
            {
                FileName = bootstrapper,
                Arguments = "/silent /install",
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden
            }) ?? throw new InvalidOperationException("Не удалось запустить установщик WebView2.", firstException);
            await process.WaitForExitAsync(lifetime.Token);
            if (process.ExitCode != 0)
            {
                throw new InvalidOperationException($"Установщик WebView2 завершился с кодом {process.ExitCode}.", firstException);
            }

            await PreviewView.EnsureCoreWebView2Async();
        }
    }

    private async Task LoadInstalledTexturesAsync()
    {
        var player = PlayerNameTextBox.Text.Trim();
        if (!PlayerCosmeticsClient.IsValidNickname(player))
        {
            return;
        }

        var skinPath = localStore.GetInstalledPath(player, CosmeticTextureKind.Skin);
        var capePath = localStore.GetInstalledPath(player, CosmeticTextureKind.Cape);
        if (File.Exists(skinPath)) selectedSkinPath = skinPath;
        if (File.Exists(capePath)) selectedCapePath = capePath;
        if (selectedSkinPath is not null || selectedCapePath is not null)
        {
            SourceLabel.Text = "Загружены последние локальные текстуры";
            await SendPreviewAsync();
        }
    }

    private async Task LoadCatalogAsync(bool reset)
    {
        if (reset)
        {
            currentPage = 1;
            catalogItems.Clear();
        }

        try
        {
            SetStatus(reset ? "Загружаем каталог скинов…" : "Загружаем следующую страницу…");
            var query = new CosmeticCatalogQuery(
                currentPage,
                GetTag(SortComboBox) ?? "best",
                GetTag(TypeComboBox) ?? "any",
                TagsTextBox.Text.Trim(),
                SensitiveCheckBox.IsChecked == true);
            var page = await catalogClient.GetPageAsync(query, lifetime.Token);
            catalogItems.AddRange(page.Items.Select(item => new CatalogItemViewModel(item)));
            CatalogListBox.ItemsSource = null;
            CatalogListBox.ItemsSource = catalogItems.ToArray();
            hasNextPage = page.HasNext;
            NextPageButton.IsEnabled = hasNextPage;
            CatalogPageLabel.Text = $"Страница {page.Page} · {catalogItems.Count} загружено из {page.TotalItems:N0}";
            SetStatus(page.Diagnostics.Count == 0
                ? "Каталог готов. Выберите скин для предпросмотра."
                : "Каталог загружен с предупреждениями.");
        }
        catch (OperationCanceledException) when (lifetime.IsCancellationRequested)
        {
        }
        catch (Exception exception)
        {
            SetStatus($"Каталог недоступен: {exception.Message}");
            CatalogPageLabel.Text = "Каталог недоступен · можно загрузить PNG-файл";
            NextPageButton.IsEnabled = false;
        }
    }

    private async void LoadCatalog_Click(object sender, RoutedEventArgs e) => await LoadCatalogAsync(reset: true);

    private async void NextPage_Click(object sender, RoutedEventArgs e)
    {
        if (!hasNextPage) return;
        currentPage++;
        await LoadCatalogAsync(reset: false);
    }

    private async void CatalogFilterChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (initialized) await LoadCatalogAsync(reset: true);
    }

    private async void CatalogSelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (CatalogListBox.SelectedItem is not CatalogItemViewModel item) return;
        try
        {
            SetStatus($"Скачиваем скин №{item.Id} для предпросмотра…");
            selectedSkinPath = await localStore.CacheRemoteAsync(httpClient, item.TextureUrl, CosmeticTextureKind.Skin, lifetime.Token);
            ModelComboBox.SelectedIndex = item.IsSlim ? 2 : 1;
            SourceLabel.Text = $"Ely.by · скин №{item.Id}";
            await SendPreviewAsync();
            SetStatus("Скин загружен в предпросмотр.");
        }
        catch (Exception exception)
        {
            SetStatus($"Скин не загрузился: {exception.Message}");
        }
    }

    private async void ResolveByNickname_Click(object sender, RoutedEventArgs e)
    {
        var player = PlayerNameTextBox.Text.Trim();
        if (!PlayerCosmeticsClient.IsValidNickname(player))
        {
            SetStatus("Укажите ник из 3–16 символов A–Z, 0–9 или _.");
            return;
        }

        try
        {
            SetStatus($"Ищем профиль {player} в Mojang и Ely.by…");
            var profile = await playerCosmeticsClient.ResolveByNicknameAsync(player, lifetime.Token);
            await LoadCapesForPlayerAsync(player, updateStatus: false);
            if (profile is null || (profile.SkinUrl is null && profile.CapeUrl is null))
            {
                SetStatus(capeItems.Count == 0
                    ? "Для этого ника текстуры не найдены. Можно загрузить свой файл."
                    : "Плащи для ника загружены слева; скин можно загрузить своим файлом.");
                return;
            }

            if (profile.SkinUrl is not null)
            {
                selectedSkinPath = await localStore.CacheRemoteAsync(httpClient, profile.SkinUrl, CosmeticTextureKind.Skin, lifetime.Token);
                ModelComboBox.SelectedIndex = profile.IsSlim ? 2 : 1;
            }
            else
            {
                selectedSkinPath = null;
            }

            if (profile.CapeUrl is not null)
            {
                selectedCapePath = await localStore.CacheRemoteAsync(httpClient, profile.CapeUrl, CosmeticTextureKind.Cape, lifetime.Token);
            }
            else
            {
                selectedCapePath = null;
            }

            SourceLabel.Text = $"{profile.Source} · профиль {profile.PlayerName}";
            await SendPreviewAsync();
            SetStatus("Профиль загружен в предпросмотр. Примените нужные элементы кнопками справа.");
        }
        catch (Exception exception)
        {
            SetStatus($"Профиль не загрузился: {exception.Message}");
        }
    }

    private async void LoadCapes_Click(object sender, RoutedEventArgs e)
    {
        var player = PlayerNameTextBox.Text.Trim();
        if (!PlayerCosmeticsClient.IsValidNickname(player))
        {
            SetStatus("Укажите ник из 3–16 символов A–Z, 0–9 или _.");
            return;
        }

        await LoadCapesForPlayerAsync(player, updateStatus: true);
    }

    private async Task LoadCapesForPlayerAsync(string player, bool updateStatus)
    {
        try
        {
            if (updateStatus) SetStatus($"Ищем плащи для {player} в capes.dev…");
            var items = await capesClient.GetPlayerCapesAsync(player, lifetime.Token);
            capeItems.Clear();
            capeItems.AddRange(items.Select(item => new CapeCatalogItemViewModel(item)));
            CapeListBox.ItemsSource = null;
            CapeListBox.ItemsSource = capeItems.ToArray();
            if (updateStatus)
            {
                SetStatus(items.Count == 0
                    ? "Для этого ника capes.dev не нашёл плащей. Можно загрузить плащ PNG-файлом."
                    : $"Найдено плащей: {items.Count}. Выберите вариант слева для предпросмотра.");
            }
        }
        catch (OperationCanceledException) when (lifetime.IsCancellationRequested)
        {
        }
        catch (Exception exception)
        {
            capeItems.Clear();
            CapeListBox.ItemsSource = null;
            if (updateStatus) SetStatus($"Каталог плащей недоступен: {exception.Message}");
        }
    }

    private async void CapeSelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (CapeListBox.SelectedItem is not CapeCatalogItemViewModel item) return;
        try
        {
            SetStatus($"Скачиваем плащ {item.Type} для предпросмотра…");
            selectedCapePath = await localStore.CacheRemoteAsync(httpClient, item.TextureUrl, CosmeticTextureKind.Cape, lifetime.Token);
            SourceLabel.Text = $"{item.Source} · плащ {item.Type}";
            await SendPreviewAsync();
            SetStatus("Плащ загружен в предпросмотр.");
        }
        catch (Exception exception)
        {
            SetStatus($"Плащ не загрузился: {exception.Message}");
        }
    }

    private async void ImportSkin_Click(object sender, RoutedEventArgs e) => await ImportTextureAsync(CosmeticTextureKind.Skin);

    private async void ImportCape_Click(object sender, RoutedEventArgs e) => await ImportTextureAsync(CosmeticTextureKind.Cape);

    private async Task ImportTextureAsync(CosmeticTextureKind kind)
    {
        var dialog = new OpenFileDialog
        {
            Title = kind == CosmeticTextureKind.Skin ? "Выберите PNG/JPG скин" : "Выберите PNG/JPG плащ",
            Filter = "Изображения|*.png;*.jpg;*.jpeg;*.bmp;*.gif|Все файлы|*.*",
            CheckFileExists = true,
            Multiselect = false
        };
        if (dialog.ShowDialog(this) != true) return;

        try
        {
            var imported = ConvertToPng(dialog.FileName);
            _ = SkinTextureValidator.ValidateFile(imported, kind);
            if (kind == CosmeticTextureKind.Skin)
            {
                selectedSkinPath = imported;
                SourceLabel.Text = $"Локальный файл скина · {Path.GetFileName(dialog.FileName)}";
            }
            else
            {
                selectedCapePath = imported;
                SourceLabel.Text = $"Локальный файл плаща · {Path.GetFileName(dialog.FileName)}";
            }

            await SendPreviewAsync();
            SetStatus("Файл загружен. Проверьте модель и нажмите «Применить».");
        }
        catch (Exception exception)
        {
            SetStatus($"Файл не принят: {exception.Message}");
        }
    }

    private void ApplySkin_Click(object sender, RoutedEventArgs e) => ApplyTexture(selectedSkinPath, CosmeticTextureKind.Skin);

    private void ApplyCape_Click(object sender, RoutedEventArgs e) => ApplyTexture(selectedCapePath, CosmeticTextureKind.Cape);

    private void ApplyTexture(string? source, CosmeticTextureKind kind)
    {
        var player = PlayerNameTextBox.Text.Trim();
        if (source is null || !File.Exists(source))
        {
            SetStatus(kind == CosmeticTextureKind.Skin ? "Сначала выберите скин." : "Сначала выберите плащ.");
            return;
        }

        try
        {
            var path = localStore.InstallFile(source, player, kind);
            SetStatus(kind == CosmeticTextureKind.Skin
                ? $"Скин применён к нику {player}."
                : $"Плащ применён к нику {player}.");
            SourceLabel.Text = $"Установлено: {path}";
        }
        catch (Exception exception)
        {
            SetStatus($"Текстура не применена: {exception.Message}");
        }
    }

    private async void PreviewSettingChanged(object sender, RoutedEventArgs e) => await SendPreviewAsync();

    private async Task SendPreviewAsync()
    {
        if (!previewReady || PreviewView.CoreWebView2 is null) return;
        await previewGate.WaitAsync(lifetime.Token);
        try
        {
            var skinPreview = await CopyPreviewTextureAsync(selectedSkinPath, "current-skin.png");
            var capePreview = await CopyPreviewTextureAsync(selectedCapePath, "current-cape.png");
            var config = new
            {
                skinUrl = skinPreview is null ? null : "https://copimine.local/current-skin.png",
                capeUrl = capePreview is null ? null : "https://copimine.local/current-cape.png",
                model = GetTag(ModelComboBox) ?? "auto-detect",
                animation = GetTag(AnimationComboBox) ?? "walking",
                background = GetTag(BackgroundComboBox) ?? "ice",
                autoRotate = AutoRotateCheckBox.IsChecked == true,
                animationSpeed = AnimationSpeedSlider.Value
            };
            var json = JsonSerializer.Serialize(config);
            await File.WriteAllTextAsync(Path.Combine(previewRoot, "preview-config.json"), json, lifetime.Token);
            PreviewView.CoreWebView2.PostWebMessageAsJson(json);
        }
        finally
        {
            previewGate.Release();
        }
    }

    private async Task<string?> CopyPreviewTextureAsync(string? source, string fileName)
    {
        if (string.IsNullOrWhiteSpace(source) || !File.Exists(source)) return null;
        var destination = Path.Combine(previewRoot, fileName);
        await Task.Run(() => File.Copy(source, destination, overwrite: true), lifetime.Token);
        return destination;
    }

    private string ConvertToPng(string source)
    {
        var directory = Path.Combine(launcherDataRoot, "cosmetics", "imports");
        Directory.CreateDirectory(directory);
        var destination = Path.Combine(directory, $"{Guid.NewGuid():N}.png");
        var decoder = BitmapDecoder.Create(
            new Uri(Path.GetFullPath(source), UriKind.Absolute),
            BitmapCreateOptions.PreservePixelFormat,
            BitmapCacheOption.OnLoad);
        if (decoder.Frames.Count == 0) throw new InvalidDataException("Изображение не содержит кадров.");
        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(decoder.Frames[0]));
        using var output = File.Create(destination);
        encoder.Save(output);
        return destination;
    }

    private void CopyPreviewAsset(string name)
    {
        var source = Path.Combine(AppContext.BaseDirectory, "Assets", "SkinPreview", name);
        var destination = Path.Combine(previewRoot, name);
        if (!File.Exists(source)) throw new FileNotFoundException("Файл предпросмотра не найден.", source);
        File.Copy(source, destination, overwrite: true);
    }

    private async Task WritePreviewConfigAsync()
    {
        var config = "{\"skinUrl\":null,\"capeUrl\":null,\"model\":\"auto-detect\",\"animation\":\"walking\",\"background\":\"ice\",\"autoRotate\":true,\"animationSpeed\":1}";
        await File.WriteAllTextAsync(Path.Combine(previewRoot, "preview-config.json"), config, lifetime.Token);
    }

    private void PreviewMessageReceived(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        try
        {
            using var document = JsonDocument.Parse(e.WebMessageAsJson);
            if (document.RootElement.TryGetProperty("type", out var type)
                && type.GetString() == "preview-error"
                && document.RootElement.TryGetProperty("message", out var message))
            {
                PreviewStatus.Text = "Ошибка текстуры: " + message.GetString();
            }
        }
        catch (JsonException)
        {
            PreviewStatus.Text = "Ошибка предпросмотра";
        }
    }

    private static string? GetTag(System.Windows.Controls.ComboBox comboBox) =>
        (comboBox.SelectedItem as System.Windows.Controls.ComboBoxItem)?.Tag?.ToString();

    private void SetStatus(string message)
    {
        StatusText.Text = message;
        PreviewStatus.Text = message;
    }

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    private void OnClosed(object? sender, EventArgs e)
    {
        lifetime.Cancel();
        if (PreviewView.CoreWebView2 is { } core)
        {
            core.WebMessageReceived -= PreviewMessageReceived;
        }
        httpClient.Dispose();
        previewGate.Dispose();
        lifetime.Dispose();
    }

    private sealed class CatalogItemViewModel(CosmeticCatalogItem item)
    {
        public string Id => item.Id;
        public Uri TextureUrl => item.TextureUrl;
        public bool IsSlim => item.IsSlim;
        public string Tags => item.Tags.Count == 0 ? "Без тегов" : string.Join(", ", item.Tags.Take(4));
    }

    private sealed class CapeCatalogItemViewModel(CapeCatalogItem item)
    {
        public string Type => item.Type;
        public Uri TextureUrl => item.TextureUrl;
        public string PlayerName => item.PlayerName;
        public bool Animated => item.IsAnimated;
        public string Source => item.Source;
    }
}
