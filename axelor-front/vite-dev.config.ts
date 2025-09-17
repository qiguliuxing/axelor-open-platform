import react from "@vitejs/plugin-react";
import jotaiDebugLabel from "jotai/babel/plugin-debug-label";
import jotaiReactRefresh from "jotai/babel/plugin-react-refresh";
import { ProxyOptions, loadEnv, mergeConfig } from "vite";
import { ViteUserConfig, defineConfig } from "vitest/config";
import viteConfig from "./vite.config";

const env = loadEnv("dev", process.cwd(), "");
let base = env.VITE_PROXY_CONTEXT ?? "/";

base = base.endsWith("/") ? base : `${base}/`;
const unslashedBase = base === "/" ? base : base.slice(0, -1);

const { plugins, ...conf } = viteConfig as ViteUserConfig;

// replace react plugin
plugins[0] = react({
  babel: {
    plugins: [jotaiDebugLabel, jotaiReactRefresh],
  },
});

const proxyAll: ProxyOptions = {
  target: env.VITE_PROXY_TARGET,
  changeOrigin: true,
  xfwd: true,
  bypass(req, res, options) {
    if (
      req.url === base ||
      req.url === base + "index.html" ||
      req.url.startsWith(base + "src/") ||
      req.url.startsWith(base + "@fs/") ||
      req.url === base + "@react-refresh" ||
      req.url.startsWith(base + "@id/") ||
      req.url.startsWith(base + "@vite/") ||
      req.url.startsWith(base + "node_modules/") ||
      /\/theme\/([^.]+)\.json/.test(req.url) ||
      req.url.startsWith(base + "js/libs/monaco-editor/vs/")
    ) {
      return req.url;
    }
  },
};

const proxyWs: ProxyOptions = {
  target: env.VITE_PROXY_TARGET,
  changeOrigin: true,
  ws: true,
};

export default mergeConfig(
  conf,
  defineConfig({
    plugins,
    base,
    server: {
      proxy: {
        [`${base}websocket`]: proxyWs,
        [base]: proxyAll,
        [unslashedBase]: proxyAll,
      },
      fs: {
        // Allow serving files from one level up to the project root
        allow: ["..", "../../axelor-ui"],
      },
    },
  }),
);
