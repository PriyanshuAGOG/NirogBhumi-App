import * as http from 'http';
import * as fs from 'fs';
import * as path from 'path';

const PORT = Number(process.env.PORT) || 3000;
const DEBUG_APK_PATH = path.join(process.cwd(), 'nirog_bhumi_debug.apk');
const RELEASE_APK_PATH = path.join(process.cwd(), 'nirog_bhumi_release.apk');

const server = http.createServer((req, res) => {
  const url = req.url || '/';

  // Serve the index/landing page
  if (url === '/' || url === '/index.html') {
    let debugSizeMB = 'Unknown';
    let releaseSizeMB = 'Unknown';

    try {
      if (fs.existsSync(DEBUG_APK_PATH)) {
        const stats = fs.statSync(DEBUG_APK_PATH);
        debugSizeMB = (stats.size / (1024 * 1024)).toFixed(2) + ' MB';
      }
    } catch (e) {
      console.error('Error reading debug APK size:', e);
    }

    try {
      if (fs.existsSync(RELEASE_APK_PATH)) {
        const stats = fs.statSync(RELEASE_APK_PATH);
        releaseSizeMB = (stats.size / (1024 * 1024)).toFixed(2) + ' MB';
      }
    } catch (e) {
      console.error('Error reading release APK size:', e);
    }

    const html = `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Nirog Bhumi - Wellness Delivery Portal</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <style>
    @import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap');
    body {
      font-family: 'Space Grotesk', sans-serif;
    }
    .mono {
      font-family: 'JetBrains Mono', monospace;
    }
  </style>
</head>
<body class="bg-stone-50 text-stone-900 min-h-screen flex flex-col justify-between">

  <!-- Content Container -->
  <main class="max-w-4xl mx-auto px-6 py-12 flex-grow flex flex-col justify-center">

    <!-- App Logo / Greeting -->
    <div class="text-center mb-10">
      <span class="inline-block px-3 py-1 bg-emerald-100 text-emerald-800 rounded-full text-xs font-semibold tracking-wide uppercase mb-4">
        🌱 Nirog Bhumi Delivery Portal
      </span>
      <h1 class="text-4xl md:text-5xl font-bold tracking-tight text-stone-900 mb-3">
        Nirog Bhumi
      </h1>
      <p class="text-lg text-stone-600 max-w-xl mx-auto">
        A calm, lifestyle-first clinical platform for diabetes reversal, hypertension control, mindful sleep, and habit optimization.
      </p>
    </div>

    <!-- Main Grid layout for APK Downloads -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">

      <!-- Card 1: Production Release APK (Ready for Play Store) -->
      <div id="release-card" class="bg-white rounded-2xl border-2 border-emerald-500/30 shadow-sm p-6 md:p-8 flex flex-col justify-between transition-all hover:shadow-md relative overflow-hidden">
        <div class="absolute top-0 right-0 bg-emerald-500 text-white text-[10px] uppercase font-bold tracking-wider px-3 py-1 rounded-bl-xl shadow-sm">
          Recommended
        </div>
        <div>
          <!-- Icon -->
          <div class="w-12 h-12 bg-emerald-50 rounded-xl flex items-center justify-center mb-5 border border-emerald-100">
            <svg class="w-6 h-6 text-emerald-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m5h-1a3 3 0 00-3-3V7a3 3 0 00-3-3H9a3 3 0 00-3 3v2a3 3 0 00-3 3v1a3 3 0 00-3 3v1a3 3 0 003 3h14a3 3 0 003-3v-1a3 3 0 00-3-3z" />
            </svg>
          </div>

          <h2 class="text-xl font-bold text-stone-900 mb-1">Play Store Release APK</h2>
          <span class="inline-block px-2.5 py-0.5 bg-emerald-50 text-emerald-700 rounded-md text-xs font-medium mb-4">
            Production Build Signed
          </span>
          <p class="text-stone-500 text-sm mb-6 leading-relaxed">
            Compiled with strict compiler optimizations, optimized resource handling, and production-ready signatures. Perfect to go live directly to Google Play Store or deploy to production devices.
          </p>
        </div>

        <div>
          <!-- Download Action -->
          <a href="/download-release" class="inline-flex items-center justify-center w-full px-5 py-3 bg-emerald-600 hover:bg-emerald-700 text-white font-semibold rounded-xl shadow-sm transition-all text-sm">
            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            Download Release APK
          </a>

          <!-- Specs -->
          <div class="mt-3 flex items-center justify-between text-xs text-stone-400 mono px-1">
            <span>Size: <strong class="text-stone-600 font-medium">${releaseSizeMB}</strong></span>
            <span>File: <span class="text-stone-600">nirog_bhumi_release.apk</span></span>
          </div>
        </div>
      </div>

      <!-- Card 2: Diagnostics Debug APK (Ideal for Sandboxed QA) -->
      <div id="debug-card" class="bg-white rounded-2xl border border-stone-200 shadow-sm p-6 md:p-8 flex flex-col justify-between transition-all hover:shadow-md">
        <div>
          <!-- Icon -->
          <div class="w-12 h-12 bg-amber-50 rounded-xl flex items-center justify-center mb-5 border border-amber-100">
            <svg class="w-6 h-6 text-amber-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>

          <h2 class="text-xl font-bold text-stone-900 mb-1">Sandbox Debug APK</h2>
          <span class="inline-block px-2.5 py-0.5 bg-amber-50 text-amber-700 rounded-md text-xs font-medium mb-4">
            Debug-Sign Enabled
          </span>
          <p class="text-stone-500 text-sm mb-6 leading-relaxed">
            Contains active debugging endpoints, detailed logcats, and local testing configurations. Ideal for quick side-load evaluation, diagnostics, sandboxed validation, or simulator deployment.
          </p>
        </div>

        <div>
          <!-- Download Action -->
          <a href="/download-debug" class="inline-flex items-center justify-center w-full px-5 py-3 bg-stone-800 hover:bg-stone-900 text-white font-semibold rounded-xl shadow-sm transition-all text-sm">
            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            Download Debug APK
          </a>

          <!-- Specs -->
          <div class="mt-3 flex items-center justify-between text-xs text-stone-400 mono px-1">
            <span>Size: <strong class="text-stone-600 font-medium">${debugSizeMB}</strong></span>
            <span>File: <span class="text-stone-600">nirog_bhumi_debug.apk</span></span>
          </div>
        </div>
      </div>

    </div>

    <!-- Highlights Section -->
    <div id="highlights-section" class="bg-white rounded-2xl border border-stone-200/80 p-6 md:p-8 mb-8">
      <h3 class="text-xs font-semibold text-stone-400 uppercase tracking-wider mb-6">Built-in Clinical Features & Design Architecture</h3>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div class="flex items-start">
          <div class="w-6 h-6 text-emerald-500 mr-3 flex-shrink-0 mt-0.5">🌱</div>
          <div>
            <h4 class="text-sm font-semibold text-stone-800">Diabetes & Hypertension Remission</h4>
            <p class="text-xs text-stone-500 leading-relaxed">Integrated logging parameters for food metrics, systemic responses, glucose indices, and cardiovascular tracking.</p>
          </div>
        </div>
        <div class="flex items-start">
          <div class="w-6 h-6 text-emerald-500 mr-3 flex-shrink-0 mt-0.5">🏃</div>
          <div>
            <h4 class="text-sm font-semibold text-stone-800">Complete Habit Tracker Stack</h4>
            <p class="text-xs text-stone-500 leading-relaxed">Interactive sleep trackers, real-time hydration meters, step walks, and task lists configured with durable persistence.</p>
          </div>
        </div>
        <div class="flex items-start">
          <div class="w-6 h-6 text-emerald-500 mr-3 flex-shrink-0 mt-0.5">🧘</div>
          <div>
            <h4 class="text-sm font-semibold text-stone-800">Meditation & Stress Audits</h4>
            <p class="text-xs text-stone-500 leading-relaxed">Curated breathing routines with animated cycles, sound-aided cycles, and progress tracking statistics.</p>
          </div>
        </div>
        <div class="flex items-start">
          <div class="w-6 h-6 text-emerald-500 mr-3 flex-shrink-0 mt-0.5">📱</div>
          <div>
            <h4 class="text-sm font-semibold text-stone-800">Aesthetic Jetpack Compose Design</h4>
            <p class="text-xs text-stone-500 leading-relaxed">Modern material design curves, dark modes, tactile gestures, smooth navigation transitions, and state preservation.</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Deployment Summary Card -->
    <div id="deployment-card" class="bg-stone-100 rounded-xl border border-stone-200 p-5 mono text-xs text-stone-500 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
      <div>
        <div class="text-stone-700 font-medium mb-1 flex items-center">
          <span class="w-2.5 h-2.5 rounded-full bg-emerald-500 inline-block mr-2 animate-pulse"></span>
          Deployment Status: Live & Optimized
        </div>
        <div>Active Containers: Cloud Run Dual Targets</div>
      </div>
      <div class="text-right sm:text-left">
        <div>Release Profile: Production Ready (V1.0)</div>
        <div>Ready Date: ${new Date().toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' })}</div>
      </div>
    </div>

  </main>

  <!-- Footer -->
  <footer class="w-full text-center py-6 border-t border-stone-200/50 text-xs text-stone-400">
    Designed for Nirog Bhumi Wellness • Crafted via Google AI Studio
  </footer>

</body>
</html>
    `;

    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(html);
    return;
  }

  // Route: Download Release APK
  if (url === '/download-release' || url === '/nirog_bhumi_release.apk') {
    if (fs.existsSync(RELEASE_APK_PATH)) {
      const stat = fs.statSync(RELEASE_APK_PATH);
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Length': stat.size,
        'Content-Disposition': 'attachment; filename=nirog_bhumi_release.apk'
      });
      const readStream = fs.createReadStream(RELEASE_APK_PATH);
      readStream.pipe(res);
    } else {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Error: Production Release APK not found. Ensure "gradle assembleRelease" was executed successfully.');
    }
    return;
  }

  // Route: Download Debug APK
  if (url === '/download-debug' || url === '/nirog_bhumi_debug.apk' || url === '/download') {
    if (fs.existsSync(DEBUG_APK_PATH)) {
      const stat = fs.statSync(DEBUG_APK_PATH);
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Length': stat.size,
        'Content-Disposition': 'attachment; filename=nirog_bhumi_debug.apk'
      });
      const readStream = fs.createReadStream(DEBUG_APK_PATH);
      readStream.pipe(res);
    } else {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Error: Sandbox Debug APK not found. Ensure "gradle assembleDebug" was executed successfully.');
    }
    return;
  }

  // Fallback 404
  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

server.listen(PORT, () => {
  console.log(`Server is listening on port ${PORT}`);
});
