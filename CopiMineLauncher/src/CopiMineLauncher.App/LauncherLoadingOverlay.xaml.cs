using System.Windows;
using System.Windows.Controls;

namespace CopiMineLauncher.App;

public partial class LauncherLoadingOverlay : UserControl
{
    public static readonly DependencyProperty IsSplashVisibleProperty = DependencyProperty.Register(
        nameof(IsSplashVisible), typeof(bool), typeof(LauncherLoadingOverlay), new PropertyMetadata(false));

    public static readonly DependencyProperty IsOperationVisibleProperty = DependencyProperty.Register(
        nameof(IsOperationVisible), typeof(bool), typeof(LauncherLoadingOverlay), new PropertyMetadata(false));

    public static readonly DependencyProperty StageProperty = DependencyProperty.Register(
        nameof(Stage), typeof(string), typeof(LauncherLoadingOverlay), new PropertyMetadata(string.Empty));

    public static readonly DependencyProperty ProgressProperty = DependencyProperty.Register(
        nameof(Progress), typeof(double), typeof(LauncherLoadingOverlay), new PropertyMetadata(0d));

    public static readonly DependencyProperty IsIndeterminateProperty = DependencyProperty.Register(
        nameof(IsIndeterminate), typeof(bool), typeof(LauncherLoadingOverlay), new PropertyMetadata(false));

    public static readonly DependencyProperty ProgressLabelProperty = DependencyProperty.Register(
        nameof(ProgressLabel), typeof(string), typeof(LauncherLoadingOverlay), new PropertyMetadata("0%"));

    public static readonly DependencyProperty ReducedMotionProperty = DependencyProperty.Register(
        nameof(ReducedMotion), typeof(bool), typeof(LauncherLoadingOverlay), new PropertyMetadata(LauncherMotion.ReducedMotion));

    public LauncherLoadingOverlay()
    {
        InitializeComponent();
    }

    public bool IsSplashVisible
    {
        get => (bool)GetValue(IsSplashVisibleProperty);
        set => SetValue(IsSplashVisibleProperty, value);
    }

    public bool IsOperationVisible
    {
        get => (bool)GetValue(IsOperationVisibleProperty);
        set => SetValue(IsOperationVisibleProperty, value);
    }

    public string Stage
    {
        get => (string)GetValue(StageProperty);
        set => SetValue(StageProperty, value);
    }

    public double Progress
    {
        get => (double)GetValue(ProgressProperty);
        set => SetValue(ProgressProperty, value);
    }

    public bool IsIndeterminate
    {
        get => (bool)GetValue(IsIndeterminateProperty);
        set => SetValue(IsIndeterminateProperty, value);
    }

    public string ProgressLabel
    {
        get => (string)GetValue(ProgressLabelProperty);
        set => SetValue(ProgressLabelProperty, value);
    }

    public bool ReducedMotion
    {
        get => (bool)GetValue(ReducedMotionProperty);
        set => SetValue(ReducedMotionProperty, value);
    }
}
