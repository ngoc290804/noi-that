import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const proxy = { '/api': { target: env.API_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true } };
  return {
    plugins: [react()],
    build: { target: ['safari15.4', 'chrome105', 'edge105', 'firefox121'] },
    server: { port: 5173, strictPort: true, proxy },
    preview: { port: 4173, strictPort: true, proxy },
  };
});
