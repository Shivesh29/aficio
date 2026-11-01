import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/albums': 'http://localhost:8080',
      '/tracks': 'http://localhost:8080',
      '/artists': 'http://localhost:8080',
      '/api': 'http://localhost:8080',
      '/users': 'http://localhost:8080',
      '/catalog': 'http://localhost:8080',
      '/musicbrainz': 'http://localhost:8080',
      '/api/reviews': 'http://localhost:8080',
      '/api/lists': 'http://localhost:8080',
      '/api/messages': 'http://localhost:8080',
      '/api/notifications': 'http://localhost:8080',
      '/api/feed': 'http://localhost:8080',
      '/app': 'http://localhost:8080',
      '/social-ws': {
        target: 'http://localhost:8080',
        ws: true,
      },
      '/notification-ws': {
        target: 'http://localhost:8080',
        ws: true,
      },
    },
  },
})
