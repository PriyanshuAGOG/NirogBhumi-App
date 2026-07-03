import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        // Split the Firebase SDK (the bulk of the bundle) and React out of the
        // app code, and off the per-route chunks created by App.tsx's lazy()
        // imports - so a sign-in-only visit doesn't pay for every page.
        manualChunks: {
          firebase: ['firebase/app', 'firebase/auth', 'firebase/firestore', 'firebase/functions'],
          vendor: ['react', 'react-dom', 'react-router-dom'],
        },
      },
    },
  },
})
