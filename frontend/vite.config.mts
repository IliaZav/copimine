import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ command }) => ({
  root: ".",
  publicDir: false,
  plugins: [
    react(),
    {
      name: "copimine-react-development-entry",
      transformIndexHtml(html) {
        if (command !== "serve") return html;
        return html.replace(
          /<script defer src="assets\/js\/copimine-react\.v1\.js[^"]*"><\/script>/,
          '<script type="module" src="/src/main.tsx"></script>',
        );
      },
    },
  ],
  define: {
    "process.env.NODE_ENV": JSON.stringify(command === "build" ? "production" : "development"),
  },
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/api/v6": { target: process.env.COPIMINE_API_ORIGIN || "http://127.0.0.1:8000" },
      "/api/v7": { target: process.env.COPIMINE_API_ORIGIN || "http://127.0.0.1:8000" },
    },
  },
  build: {
    outDir: "assets/js",
    emptyOutDir: false,
    copyPublicDir: false,
    lib: {
      entry: "src/main.tsx",
      name: "CopiMineReact",
      formats: ["es"],
      fileName: () => "copimine-react.v1.js",
      cssFileName: "copimine-react.v1",
    },
  },
}));
