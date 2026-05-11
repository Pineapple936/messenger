import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const gatewayTarget = "http://localhost:8080";

function extractToken(url?: string): string | null {
  if (!url) {
    return null;
  }

  try {
    const parsed = new URL(url, "http://localhost");
    return parsed.searchParams.get("token");
  } catch {
    return null;
  }
}

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/auth": {
        target: gatewayTarget,
        changeOrigin: true
      },
      "/user": {
        target: gatewayTarget,
        changeOrigin: true
      },
      "/chat": {
        target: gatewayTarget,
        changeOrigin: true
      },
      "/reaction": {
        target: gatewayTarget,
        changeOrigin: true
      },
      "/media": {
        target: gatewayTarget,
        changeOrigin: true
      },
      "/message": {
        target: gatewayTarget,
        changeOrigin: true,
        ws: true,
        configure(proxy) {
          proxy.on("proxyReqWs", (proxyReq, req) => {
            const token = extractToken(req.url);
            if (token) {
              proxyReq.setHeader("Authorization", `Bearer ${token}`);
            }
          });
        }
      },
      "/ws-message": {
        target: gatewayTarget,
        changeOrigin: true,
        ws: true,
        rewrite: (path) => path.replace(/^\/ws-message/, "/message"),
        configure(proxy) {
          proxy.on("proxyReqWs", (proxyReq, req) => {
            const token = extractToken(req.url);
            if (token) {
              proxyReq.setHeader("Authorization", `Bearer ${token}`);
            }
          });
        }
      }
    }
  }
});
