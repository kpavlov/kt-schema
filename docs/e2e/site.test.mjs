// Browser tests for the built site. Run `make site-test` (builds the site first), or
// `node --test docs/e2e/` after `make site`. Needs Node 22+ and Chrome/Chromium (see browser.mjs).
import assert from "node:assert/strict"
import { after, before, describe, it } from "node:test"
import { existsSync, readFileSync } from "node:fs"
import { join } from "node:path"
import { SITE_DIR, launchBrowser, serveSite } from "./browser.mjs"

const DESKTOP = { width: 1280, height: 800 }
const MOBILE = { width: 390, height: 844, mobile: true }

// The sitemap lists exactly the pages Hugo built, so new guides are covered automatically.
const pages = [...readFileSync(join(SITE_DIR, "sitemap.xml"), "utf8").matchAll(/<loc>https:\/\/[^/]+\/kt-schema\/([^<]*)<\/loc>/g)].map((m) => m[1])
const hasApiDocs = existsSync(join(SITE_DIR, "api", "index.html"))

let site, browser
before(async () => {
  // `hugo server` renders into docs/public too, with live reload and without checking the built bundle.
  assert.ok(!readFileSync(join(SITE_DIR, "index.html"), "utf8").includes("livereload.js"),
    `${SITE_DIR} holds a hugo server build: stop \`make site-dev\` and run \`make site\`, or point SITE_DIR at another bundle`)
  site = await serveSite(); browser = await launchBrowser()
})
after(async () => { try { await browser.close() } finally { site.close() } })

/** Opens [path] in a fresh tab with the given viewport, runs [body], and always closes the tab. */
async function withPage(viewport, path, body) {
  const page = await browser.newPage(viewport)
  try {
    await page.goto(site.baseUrl + path)
    await body(page)
  } finally {
    await page.close()
  }
}

const SEARCH_INPUT = `deep("pagefind-searchbox input").length > 0`

// Anchors rendered by the searchbox dropdown, as opposed to page content or the nav.
const SEARCH_RESULTS = `(() => { const box = document.querySelector("pagefind-searchbox")
  return [...new Set(deep("a[href]", box.shadowRoot ?? box).filter(shown).map((a) => a.getAttribute("href")))] })()`

describe("every page", () => {
  it("is discovered from the sitemap", () => {
    assert.ok(pages.length >= 5, `expected home plus guides, got ${JSON.stringify(pages)}`)
  })

  for (const [name, viewport] of [["desktop", DESKTOP], ["mobile", MOBILE]]) {
    for (const path of pages) {
      it(`renders cleanly on ${name}: /${path}`, () => withPage(viewport, path, async (page) => {
        const r = await page.eval(`(async () => {
          const internal = [...new Set([...document.links].filter((a) => a.origin === location.origin).map((a) => a.origin + a.pathname))]
          return {
            title: document.title, h1: document.querySelector("h1")?.textContent,
            overflow: document.documentElement.scrollWidth > innerWidth,
            brokenImages: [...document.images].filter((i) => !i.complete || i.naturalWidth === 0).map((i) => i.src),
            brokenLinks: (await Promise.all(internal.map((u) => fetch(u).then((x) => [u, x.status])))).filter(([, s]) => s !== 200),
            leaked: /\\[!(TIP|NOTE|IMPORTANT|WARNING|CAUTION)\\]|<!---|INCLUDE/.test(document.querySelector("main").innerText),
          } })()`)
        assert.ok(r.title && r.h1, "page needs a <title> and an <h1>")
        assert.deepEqual(page.problems, [], "console errors or failed requests")
        assert.equal(r.overflow, false, "page scrolls horizontally")
        assert.deepEqual(r.brokenImages, [])
        assert.deepEqual(r.brokenLinks, [])
        assert.equal(r.leaked, false, "raw alert syntax or knit markers leaked into the page")
      }))
    }
  }
})

describe("header", () => {
  const menuLinks = pages.length // every page declares a `menus.main` entry in its front matter
  const HEADER = `(() => { const docs = document.getElementById("site-nav"), project = document.querySelector("nav.project"), toggle = document.querySelector(".menu-toggle")
    return { docsLinks: [...docs.querySelectorAll("a")].filter(shown).length,
             projectLinks: [...project.querySelectorAll("a")].filter(shown).map((a) => a.getAttribute("aria-label") ?? a.textContent.trim()),
             toggleShown: shown(toggle), expanded: toggle.getAttribute("aria-expanded") } })()`

  it("shows every link and no menu button on desktop", () => withPage(DESKTOP, "", async (page) => {
    assert.deepEqual(await page.eval(HEADER), { docsLinks: menuLinks, projectLinks: ["API", "GitHub"], toggleShown: false, expanded: "false" })
  }))

  it("collapses only the menu links behind a menu button on mobile, and toggles open and closed", () => withPage(MOBILE, "", async (page) => {
    const project = ["API", "GitHub"]
    assert.deepEqual(await page.eval(HEADER), { docsLinks: 0, projectLinks: project, toggleShown: true, expanded: "false" })
    await page.click(".menu-toggle")
    assert.deepEqual(await page.eval(HEADER), { docsLinks: menuLinks, projectLinks: project, toggleShown: true, expanded: "true" })
    await page.click(".menu-toggle")
    assert.equal((await page.eval(HEADER)).docsLinks, 0)
  }))

  for (const [name, viewport] of [["desktop", DESKTOP], ["mobile", MOBILE]]) {
    it(`shows GitHub as an icon-only link with a title on ${name}`, () => withPage(viewport, "", async (page) => {
      const r = await page.eval(`(() => { const a = document.querySelector("nav.project a.icon-link"), svg = a.querySelector("svg")
        return { title: a.title, label: a.getAttribute("aria-label"), href: a.href, text: a.textContent.trim(), iconWidth: svg.getBoundingClientRect().width } })()`)
      assert.equal(r.title, "GitHub")
      assert.equal(r.label, "GitHub")
      assert.match(r.href, /github\.com\/kpavlov\/kt-schema\/?$/)
      assert.equal(r.text, "", "icon-only link must not render text")
      assert.ok(r.iconWidth >= 16, `icon too small: ${r.iconWidth}px`)
    }))

    it(`aligns API and the GitHub icon to the right edge of the header on ${name}`, () => withPage(viewport, "", async (page) => {
      const r = await page.eval(`(() => { const bar = document.querySelector(".bar"), pad = parseFloat(getComputedStyle(bar).paddingRight)
        const [api, github] = [...document.querySelectorAll("nav.project a")].map((a) => a.getBoundingClientRect())
        return { contentRight: bar.getBoundingClientRect().right - pad, apiRight: api.right, githubLeft: github.left, githubRight: github.right } })()`)
      assert.ok(Math.abs(r.githubRight - r.contentRight) <= 1, `GitHub icon ends at ${r.githubRight}, header content ends at ${r.contentRight}`)
      assert.ok(r.apiRight <= r.githubLeft, "API must sit left of the GitHub icon")
    }))
  }

  it("lets the search field fill the space between the menu links and API on desktop", () => withPage(DESKTOP, "", async (page) => {
    await page.waitFor(SEARCH_INPUT)
    const r = await page.eval(`(() => { const rect = (el) => el.getBoundingClientRect()
      const links = [...document.querySelectorAll("#site-nav a")], last = rect(links[links.length - 1])
      const box = rect(document.querySelector("pagefind-searchbox")), input = rect(deep("pagefind-searchbox input")[0])
      const api = rect(document.querySelector("nav.project a"))
      return { gapBefore: box.left - last.right, gapAfter: api.left - box.right, boxWidth: box.width, inputWidth: input.width } })()`)
    assert.ok(r.gapBefore >= 0 && r.gapBefore <= 40, `gap between the last menu link and the search field: ${r.gapBefore}px`)
    assert.ok(r.gapAfter >= 0 && r.gapAfter <= 40, `gap between the search field and API: ${r.gapAfter}px`)
    assert.ok(r.boxWidth > 300, `search field should be wide, got ${r.boxWidth}px`)
    assert.ok(r.inputWidth >= r.boxWidth * 0.9, `input (${r.inputWidth}px) should fill its container (${r.boxWidth}px)`)
  }))
})

describe("search", () => {
  for (const [name, viewport] of [["desktop", DESKTOP], ["mobile", MOBILE]]) {
    it(`finds pages from the header searchbox on ${name}`, () => withPage(viewport, "ksp/", async (page) => {
      await page.waitFor(SEARCH_INPUT)
      const placeholder = await page.eval(`deep("pagefind-searchbox input")[0].placeholder`)
      assert.match(placeholder, /search documentation/i)

      await page.eval(`deep("pagefind-searchbox input")[0].focus()`)
      await page.type("polymorphism")
      const links = await page.waitFor(`(() => { const l = ${SEARCH_RESULTS}; return l.length && l })()`)

      for (const href of links) {
        const url = new URL(href, site.baseUrl)
        assert.ok(url.pathname.startsWith("/kt-schema/"), `result escapes the site prefix: ${href}`)
        assert.ok(!url.pathname.includes("/api/"), `API docs must not be indexed: ${href}`)
        assert.equal((await fetch(url)).status, 200, href)
      }
      assert.equal(await page.eval(`document.documentElement.scrollWidth > innerWidth`), false, "dropdown causes horizontal scroll")

      await page.press("Escape", "Escape")
      await page.waitFor(`${SEARCH_RESULTS}.length === 0`)
    }))
  }

  it("focuses the searchbox with Ctrl/Cmd+K on desktop", () => withPage(DESKTOP, "", async (page) => {
    await page.waitFor(SEARCH_INPUT)
    const focused = `(() => { const i = deep("pagefind-searchbox input")[0]; return i.getRootNode().activeElement === i })()`
    assert.equal(await page.eval(focused), false)
    for (const modifier of [4 /* Meta */, 2 /* Ctrl */]) {
      await page.press("k", "KeyK", modifier)
      if (await page.eval(focused)) return
    }
    assert.fail("neither Cmd+K nor Ctrl+K focused the searchbox")
  }))
})

describe("API docs", { skip: !hasApiDocs && "docs/public/api not built (run `make apidocs`)" }, () => {
  it("links back to the site from the header of the index and of a module page", async () => {
    const modulePage = readFileSync(join(SITE_DIR, "api", "index.html"), "utf8").match(/href="(kt-schema-[a-z-]+\/[^"]*index\.html)"/)?.[1]
    assert.ok(modulePage, "no module page found on the API index")
    for (const path of ["api/", "api/" + modulePage]) {
      await withPage(DESKTOP, path, async (page) => {
        const href = await page.eval(`document.getElementById("homepage-link")?.getAttribute("href")`)
        assert.equal(href, "https://kpavlov.github.io/kt-schema/", `no homepage link in the header of ${path}`)
      })
    }
  })
})
