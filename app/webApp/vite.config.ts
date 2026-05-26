import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  root: '.',
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    port: 4173,
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/sync': 'http://127.0.0.1:8080',
    },
  },
});
