export {};

declare global {
  interface Window {
    CM?: any;
    CM6Domain?: any;
    CMBlueMap?: { state?: () => any };
    CM72?: { galleryIndex?: number };
    CMAnalyticsReactModel?: import("./charts").AnalyticsModel | null;
    CMSiteMotion?: any;
    CopimineReactBridge?: { render: (model: any) => void };
    icon?: (name: string, className?: string) => string;
  }
}
