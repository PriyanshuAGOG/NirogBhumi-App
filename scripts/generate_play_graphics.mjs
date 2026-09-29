// Regenerates the Play Store icon (512x512) and feature graphic (1024x500) from the
// launcher artwork, so the store listing always matches the app icon.
// Usage: node scripts/generate_play_graphics.mjs   (needs e2e/node_modules: `npm --prefix e2e ci`)
import { createRequire } from 'node:module'
import { readFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '..')
const require = createRequire(resolve(root, 'e2e/package.json'))
const { chromium } = require('playwright-core')

const fg = readFileSync(resolve(root, 'app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png')).toString('base64')
const BG = '#F8F6EF', FOREST = '#1B3221', MUTED = '#4F5B50'
const fgSrc = `data:image/png;base64,${fg}`

// Adaptive icon canvas is 108dp with a 72dp visible centre: scale 108/72 = 1.5x and crop.
const iconHtml = `<html><body style="margin:0;width:512px;height:512px;background:${BG};overflow:hidden;position:relative">
  <img src="${fgSrc}" style="position:absolute;width:768px;height:768px;left:-128px;top:-128px"></body></html>`

const featureHtml = `<html><body style="margin:0;width:1024px;height:500px;background:${BG};overflow:hidden;position:relative;font-family:'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif">
  <div style="position:absolute;left:64px;top:106px;width:288px;height:288px;border-radius:64px;overflow:hidden;background:${BG};box-shadow:0 8px 32px rgba(27,50,33,.18)">
    <img src="${fgSrc}" style="position:absolute;width:396px;height:396px;left:-54px;top:-54px"></div>
  <div style="position:absolute;left:400px;top:132px;right:56px">
    <div style="font-size:72px;font-weight:700;color:${FOREST};letter-spacing:-1px;line-height:1.05">Nirog Bhumi</div>
    <div style="margin-top:22px;font-size:34px;color:${MUTED};line-height:1.3">Daily diabetes and metabolic health support, with your coach beside you.</div>
  </div></body></html>`

const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/opt/pw-browsers/chromium', args: ['--no-sandbox'] })
mkdirSync(resolve(root, 'play/graphics'), { recursive: true })
for (const [name, html, w, h] of [['icon-512', iconHtml, 512, 512], ['feature-graphic', featureHtml, 1024, 500]]) {
  const page = await browser.newPage({ viewport: { width: w, height: h } })
  await page.setContent(html)
  await page.screenshot({ path: resolve(root, `play/graphics/${name}.png`), omitBackground: false })
  await page.close()
}
await browser.close()
console.log('wrote play/graphics/icon-512.png and feature-graphic.png')
