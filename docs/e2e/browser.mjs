// Test harness for the built site (docs/public): a static server that mimics the GitHub Pages
// `/kt-schema/` prefix, and a minimal Chrome DevTools Protocol driver.
// Needs Node 22+ (global WebSocket) and a local Chrome/Chromium; set CHROME_BIN to override discovery.
import { spawn, spawnSync } from "node:child_process"
import { createServer } from "node:http"
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { extname, join, resolve, sep } from "node:path"

// Override with SITE_DIR to test a bundle built elsewhere (e.g. while `make site-dev` owns docs/public).
export const SITE_DIR = resolve(process.env.SITE_DIR ?? join(import.meta.dirname, "../public"))
export const SITE_PREFIX = "/kt-schema/"

const MIME = {
  ".html": "text/html; charset=utf-8", ".css": "text/css", ".js": "text/javascript", ".mjs": "text/javascript", ".json": "application/json",
  ".svg": "image/svg+xml", ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp",
  ".xml": "application/xml", ".wasm": "application/wasm",
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/** Serves [SITE_DIR] under [SITE_PREFIX]; everything else is a 404, like a GitHub project site. */
export function serveSite() {
  const server = createServer((req, res) => {
    const path = decodeURIComponent(new URL(req.url, "http://localhost").pathname)
    let file = path.startsWith(SITE_PREFIX) ? resolve(SITE_DIR, path.slice(SITE_PREFIX.length)) : null
    if (file && (file === SITE_DIR || file.startsWith(SITE_DIR + sep)) && existsSync(file)) {
      if (statSync(file).isDirectory()) file = join(file, "index.html")
    } else file = null
    if (!file || !existsSync(file)) return void res.writeHead(404).end("Not found")
    res.writeHead(200, { "content-type": MIME[extname(file)] ?? "application/octet-stream" }).end(readFileSync(file))
  })
  return new Promise((ok) => server.listen(0, "127.0.0.1", () =>
    ok({ baseUrl: `http://127.0.0.1:${server.address().port}${SITE_PREFIX}`, close: () => server.close() })))
}

function findChrome() {
  const candidates = [process.env.CHROME_BIN, "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "google-chrome", "google-chrome-stable", "chromium", "chromium-browser"].filter(Boolean)
  for (const c of candidates) {
    if (c.includes("/") ? existsSync(c) : spawnSync("which", [c]).status === 0) return c
  }
  throw new Error("Chrome not found. Install Chrome/Chromium or set CHROME_BIN.")
}

// Helpers available inside every page as globals: `deep` pierces open shadow roots, `shown` tests visibility.
const PAGE_HELPERS = `
  window.deep = (sel, root = document) => { const out = [...root.querySelectorAll(sel)]
    for (const el of root.querySelectorAll("*")) if (el.shadowRoot) out.push(...window.deep(sel, el.shadowRoot)); return out }
  window.shown = (el) => { const b = el.getBoundingClientRect(); return b.width > 0 && b.height > 0 && getComputedStyle(el).visibility !== "hidden" }`

export async function launchBrowser() {
  const dir = mkdtempSync(join(tmpdir(), "kt-schema-chrome-"))
  const chrome = spawn(findChrome(), ["--headless=new", "--disable-gpu", "--remote-debugging-port=0",
    `--user-data-dir=${dir}`, ...(process.env.CI ? ["--no-sandbox"] : []), "about:blank"], { stdio: "ignore" })
  let port
  for (let i = 0; i < 100 && !port; i++) {
    const file = join(dir, "DevToolsActivePort")
    if (existsSync(file)) port = readFileSync(file, "utf8").split("\n")[0]
    else await sleep(100)
  }
  if (!port) { chrome.kill(); throw new Error("Chrome DevTools endpoint did not start") }

  return {
    /** Opens a fresh tab, emulating a [width]x[height] viewport (`mobile` also applies the viewport meta tag). */
    async newPage({ width, height = 900, mobile = false }) {
      const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: "PUT" })).json()
      const ws = new WebSocket(target.webSocketDebuggerUrl)
      await new Promise((r) => (ws.onopen = r))
      let id = 0
      const calls = new Map(), waiters = []
      const problems = []
      ws.onmessage = ({ data }) => {
        const msg = JSON.parse(data)
        if (msg.id && calls.has(msg.id)) {
          const c = calls.get(msg.id); calls.delete(msg.id)
          msg.error ? c.reject(new Error(JSON.stringify(msg.error))) : c.resolve(msg.result)
          return
        }
        const { method, params } = msg
        if (method === "Runtime.exceptionThrown") problems.push(`exception: ${params.exceptionDetails.exception?.description ?? params.exceptionDetails.text}`)
        if (method === "Log.entryAdded" && params.entry.level === "error") problems.push(`console error: ${params.entry.text} ${params.entry.url ?? ""}`)
        if (method === "Network.responseReceived" && params.response.status >= 400) problems.push(`HTTP ${params.response.status}: ${params.response.url}`)
        if (method === "Network.loadingFailed" && !params.canceled) problems.push(`request failed: ${params.errorText}`)
        waiters.filter((w) => w.method === method).forEach((w) => w.resolve(params))
      }
      const send = (method, params = {}) => new Promise((resolve, reject) => { calls.set(++id, { resolve, reject }); ws.send(JSON.stringify({ id, method, params })) })
      const waitEvent = (method, timeout = 15000) => new Promise((resolve, reject) => {
        const w = { method, resolve }
        waiters.push(w)
        setTimeout(() => reject(new Error(`timed out waiting for ${method}`)), timeout).unref()
      })
      for (const domain of ["Page", "Runtime", "Network", "Log"]) await send(`${domain}.enable`)
      await send("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile })
      await send("Page.addScriptToEvaluateOnNewDocument", { source: PAGE_HELPERS })

      const page = {
        /** Runtime/console/network problems seen since the last [goto]. */
        problems,
        async goto(url) {
          problems.length = 0
          const loaded = waitEvent("Page.loadEventFired")
          await send("Page.navigate", { url })
          await loaded
          await sleep(300) // let web components upgrade
        },
        async eval(expression) {
          const r = await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true })
          if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? r.exceptionDetails.text)
          return r.result.value
        },
        async waitFor(expression, { timeout = 10000 } = {}) {
          for (const end = Date.now() + timeout; Date.now() < end; await sleep(100)) {
            const value = await page.eval(expression)
            if (value) return value
          }
          throw new Error(`timed out waiting for: ${expression}`)
        },
        async click(selector) {
          const { x, y } = await page.eval(`(() => { const b = document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect(); return { x: b.x + b.width / 2, y: b.y + b.height / 2 } })()`)
          for (const type of ["mousePressed", "mouseReleased"]) await send("Input.dispatchMouseEvent", { type, x, y, button: "left", clickCount: 1 })
        },
        type: (text) => send("Input.insertText", { text }),
        async press(key, code, modifiers = 0) {
          for (const type of ["keyDown", "keyUp"]) await send("Input.dispatchKeyEvent", { type, key, code, modifiers, windowsVirtualKeyCode: key.toUpperCase().charCodeAt(0) })
        },
        async screenshot(file) {
          writeFileSync(file, Buffer.from((await send("Page.captureScreenshot", { format: "png" })).data, "base64"))
        },
        async close() {
          ws.close()
          await fetch(`http://127.0.0.1:${port}/json/close/${target.id}`).catch(() => {})
        },
      }
      return page
    },
    async close() {
      const exited = new Promise((r) => chrome.once("exit", r))
      chrome.kill()
      await exited
      // Best effort: a leftover temp profile must never fail a test run.
      rmSync(dir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 })
    },
  }
}
