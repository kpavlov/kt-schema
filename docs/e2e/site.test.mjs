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
    assert.deepEqual(await page.eval(HEADER), { docsLinks: menuLinks, projectLinks: ["API Docs", "GitHub"], toggleShown: false, expanded: "false" })
  }))

  it("collapses only the menu links behind a menu button on mobile, and toggles open and closed", () => withPage(MOBILE, "", async (page) => {
    const project = ["API Docs", "GitHub"]
    assert.deepEqual(await page.eval(HEADER), { docsLinks: 0, projectLinks: project, toggleShown: true, expanded: "false" })
    await page.click(".menu-toggle")
    assert.deepEqual(await page.eval(HEADER), { docsLinks: menuLinks, projectLinks: project, toggleShown: true, expanded: "true" })
    await page.click(".menu-toggle")
    assert.equal((await page.eval(HEADER)).docsLinks, 0)
  }))

  for (const [name, viewport] of [["desktop", DESKTOP], ["mobile", MOBILE]]) {
    it(`shows GitHub as an icon plus label on ${name}`, () => withPage(viewport, "", async (page) => {
      const r = await page.eval(`(() => { const a = document.querySelector("nav.project a.icon-link"), svg = a.querySelector("svg")
        return { title: a.title, href: a.href, text: a.textContent.trim(), iconWidth: svg.getBoundingClientRect().width } })()`)
      assert.equal(r.title, "GitHub")
      assert.match(r.href, /github\.com\/kpavlov\/kt-schema\/?$/)
      assert.equal(r.text, "GitHub")
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

  it("shows the menu as a left sidebar and the search field on the right next to API on desktop", () => withPage(DESKTOP, "", async (page) => {
    await page.waitFor(SEARCH_INPUT)
    const r = await page.eval(`(() => { const rect = (el) => el.getBoundingClientRect()
      const nav = rect(document.getElementById("site-nav")), header = rect(document.querySelector("header"))
      const links = [...document.querySelectorAll("#site-nav a")].map(rect)
      const box = rect(document.querySelector("pagefind-searchbox")), input = rect(deep("pagefind-searchbox input")[0])
      const api = rect(document.querySelector("nav.project a")), main = rect(document.getElementById("content"))
      const brand = rect(document.querySelector(".brand")), version = document.querySelector("#site-nav .version")
      return { navLeft: nav.left, navTop: nav.top, headerBottom: header.bottom, navRight: nav.right, mainLeft: main.left,
               stacked: links.every((l, i) => i === 0 || l.top >= links[i - 1].bottom),
               versionText: version?.textContent, versionBelowLinks: version ? rect(version).top >= links[links.length - 1].bottom : false,
               brandToBox: box.left - brand.right, gapAfter: api.left - box.right, boxWidth: box.width, inputWidth: input.width } })()`)
    assert.equal(r.navLeft, 0, "sidebar must start at the left edge")
    assert.ok(Math.abs(r.navTop - r.headerBottom) <= 1, `sidebar top ${r.navTop} must meet the header bottom ${r.headerBottom}`)
    assert.ok(r.stacked, "sidebar links must be stacked vertically")
    assert.ok(r.mainLeft >= r.navRight, `content (${r.mainLeft}) must not overlap the sidebar (${r.navRight})`)
    assert.match(r.versionText ?? "", /^v\d+\.\d+/, "sidebar must end with the project version")
    assert.ok(r.versionBelowLinks, "version must sit below the menu links")
    assert.ok(r.brandToBox > 100, `search field should sit on the right, away from the brand (gap ${r.brandToBox}px)`)
    assert.ok(r.gapAfter >= 0 && r.gapAfter <= 80, `gap between the search field and API, with the colour scheme button between them: ${r.gapAfter}px`)
    assert.ok(r.boxWidth >= 300 && r.boxWidth <= 400, `search field should be capped, got ${r.boxWidth}px`)
    assert.ok(r.inputWidth >= 280, `input should be wide, got ${r.inputWidth}px`)
  }))
})

describe("markdown view", () => {
  const SITE = "https://kpavlov.github.io/kt-schema/"
  const MENU = ".page-actions details"

  for (const path of pages) {
    it(`serves clean markdown with working links: /${path}index.md`, async () => {
      const response = await fetch(`${site.baseUrl}${path}index.md`)
      assert.equal(response.status, 200)
      const markdown = await response.text()
      assert.match(markdown, /^# /, "starts with the page title")
      assert.doesNotMatch(markdown, /^---$\ntitle:/m, "front matter leaked")
      assert.doesNotMatch(markdown, /<!---/, "knit markers leaked")
      assert.doesNotMatch(markdown, /\]\((?!https?:|#|mailto:)[^)]*\)/, "relative link would break outside the repository")
      for (const [, target] of markdown.matchAll(new RegExp(`\\]\\(${SITE}([^)#]*)`, "g"))) {
        assert.equal((await fetch(site.baseUrl + target)).status, 200, target)
      }
    })
  }

  it("keeps XML comments inside code samples", async () => {
    assert.match(await (await fetch(`${site.baseUrl}apt/index.md`)).text(), /<!-- Only needed if you annotate/)
  })

  for (const [name, viewport] of [["desktop", DESKTOP], ["mobile", MOBILE]]) {
    it(`offers copying and viewing from a dropdown on ${name}`, () => withPage(viewport, "ksp/", async (page) => {
      assert.equal(await page.eval(`document.querySelector(".page-actions .menu").checkVisibility()`), false, "closed at first")
      await page.click(`${MENU} summary`)
      const r = await page.eval(`(() => { const menu = document.querySelector(".page-actions .menu"), box = menu.getBoundingClientRect()
        return { items: [...menu.children].map((i) => i.innerText.replace(/\\s*\\n\\s*/g, " | ")), view: menu.querySelector("a").getAttribute("href"),
          fits: box.left >= 0 && box.right <= innerWidth, overflow: document.documentElement.scrollWidth > innerWidth } })()`)
      assert.deepEqual(r.items, ["Copy page | Copy page as Markdown for LLMs", "View as Markdown | View this page as plain text"])
      assert.equal(r.view, "/kt-schema/ksp/index.md")
      assert.equal(r.fits, true, "menu must fit in the viewport")
      assert.equal(r.overflow, false, "menu causes horizontal scroll")
    }))
  }

  for (const [name, selector] of [["the main button", ".page-actions > button"], ["the menu item", ".page-actions .menu button"]]) {
    it(`copies the page markdown from ${name}`, () => withPage(DESKTOP, "ksp/", async (page) => {
      await page.eval(`window.written = null; navigator.clipboard.write = async ([item]) => { window.written = await (await item.getType("text/plain")).text() }`)
      if (selector.includes("menu")) await page.click(`${MENU} summary`)
      await page.click(selector)
      const expected = await (await fetch(`${site.baseUrl}ksp/index.md`)).text()
      await page.waitFor(`window.written !== null`)
      assert.equal(await page.eval(`window.written`), expected)
      await page.waitFor(`document.querySelector(".page-actions > button").textContent === "Copied"`)
      assert.equal(await page.eval(`document.querySelector("${MENU}").open`), false, "menu closes after choosing")
    }))
  }

  it("reports a failed copy", () => withPage(DESKTOP, "ksp/", async (page) => {
    await page.eval(`navigator.clipboard.write = async () => { throw new Error("denied") }`)
    await page.click(".page-actions > button")
    await page.waitFor(`document.querySelector(".page-actions > button").textContent === "Copy failed"`)
  }))

  it("closes the menu on Escape and on an outside click", () => withPage(DESKTOP, "ksp/", async (page) => {
    await page.click(`${MENU} summary`)
    await page.press("Escape", "Escape")
    assert.equal(await page.eval(`document.querySelector("${MENU}").open`), false)
    assert.equal(await page.eval(`document.activeElement.tagName`), "SUMMARY", "focus returns to the toggle")
    await page.click(`${MENU} summary`)
    await page.click("main h2")
    assert.equal(await page.eval(`document.querySelector("${MENU}").open`), false)
  }))

  it("opens as text in Chrome when served as text/markdown", () => withPage(DESKTOP, "ksp/index.md", async (page) => {
    assert.equal(await page.eval(`document.contentType`), "text/markdown")
    assert.match(await page.eval(`document.body.innerText`), /^# KSP Processor Configuration/)
  }))
})

describe("llms.txt", () => {
  const SITE = "https://kpavlov.github.io/kt-schema/"
  const read = async () => (await fetch(`${site.baseUrl}llms.txt`)).text()

  it("opens with the project title and summary", async () => {
    assert.match(await read(), /^# kt-schema\n\n> Generate JSON schemas .+\n\n/)
  })

  it("includes the home page markdown under its own single title", async () => {
    const home = await (await fetch(`${site.baseUrl}index.md`)).text()
    const body = home.replace(/^# .*\n+/, "")
    const llms = await read()
    assert.ok(llms.includes(`\n\n${body}\n\n## Pages\n`), "the home page body must sit between the summary and the page list")
    assert.equal(llms.match(/^# /gm).length, 1, "only one H1")
  })

  it("links the markdown of every page, once", async () => {
    const section = (await read()).split("## Pages\n")[1].split("## Optional")[0]
    const links = [...section.matchAll(/^- \[.+?\]\((.+?)\)/gm)].map((m) => m[1]).sort()
    assert.deepEqual(links, pages.map((path) => `${SITE}${path}index.md`).sort())
  })

  it("describes a guide next to its link", async () => {
    assert.match(await read(), /^- \[KSP Processor Configuration\]\(\S+\/ksp\/index\.md\): Configure the kt-schema KSP processor/m)
  })

  it("leaves the API reference and sources to the Optional section", async () => {
    const optional = (await read()).split("## Optional\n")[1]
    assert.match(optional, new RegExp(`^- \\[API reference\\]\\(${SITE}api/\\): `, "m"))
    assert.match(optional, /^- \[Source code\]\(https:\/\/github\.com\/kpavlov\/kt-schema\): /m)
  })
})

describe("mobile header", () => {
  const HEADER = `document.querySelector("header").getBoundingClientRect()`

  it("scrolls away with the page and does not slide back when scrolling up", () => withPage(MOBILE, "ksp/", async (page) => {
    assert.equal(await page.eval(`${HEADER}.top`), 0)
    await page.eval(`scrollTo(0, 1500)`)
    await page.waitFor(`${HEADER}.bottom <= 0`)
    await page.eval(`scrollTo(0, 1400)`)
    await page.eval(`new Promise((r) => setTimeout(r, 400))`)
    assert.ok(await page.eval(`${HEADER}.bottom <= 0`), "header must not come back while scrolling up")
    await page.eval(`scrollTo(0, 0)`)
    await page.waitFor(`${HEADER}.top === 0`)
  }))

  it("keeps pull to refresh", () => withPage(MOBILE, "ksp/", async (page) => {
    assert.equal(await page.eval(`getComputedStyle(document.documentElement).overscrollBehaviorY`), "auto")
  }))

  it("comes into view when the search field takes focus", () => withPage(MOBILE, "ksp/", async (page) => {
    await page.waitFor(SEARCH_INPUT)
    await page.eval(`scrollTo(0, 1500)`)
    await page.eval(`deep("pagefind-searchbox input")[0].focus()`)
    await page.waitFor(`${HEADER}.bottom > 0`)
  }))
})

describe("desktop header", () => {
  it("stays put while scrolling in both directions, without overscroll bounce", () => withPage(DESKTOP, "ksp/", async (page) => {
    for (const y of [1500, 1400, 0]) {
      await page.eval(`scrollTo(0, ${y})`)
      await page.eval(`new Promise((r) => setTimeout(r, 200))`)
      assert.equal(await page.eval(`document.querySelector("header").getBoundingClientRect().top`), 0)
    }
    assert.equal(await page.eval(`getComputedStyle(document.documentElement).overscrollBehaviorY`), "none")
  }))
})

describe("colour scheme selector", () => {
  const BACKGROUND = `getComputedStyle(document.body).backgroundColor`
  const KEYWORD = `getComputedStyle(document.querySelector(".chroma .k, .chroma .kd, .chroma .kn")).color`
  const LIGHT = "rgb(255, 255, 255)", DARK = "rgb(13, 17, 23)"
  const THEME = `document.querySelector("button.theme")`
  const choose = (page, theme) => page.eval(`while (${THEME}.dataset.choice !== "${theme}") ${THEME}.click()`)
  const emulate = (page, value) => page.send("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value }] })

  /** Runs [body] and forgets the choice: the tabs of a test run share their storage. */
  const withTheme = (viewport, path, body) => withPage(viewport, path, async (page) => {
    try { await body(page) } finally { await page.eval(`localStorage.clear()`) }
  })

  it("cycles through system, light and dark with one icon for each", () => withTheme(DESKTOP, "ksp/", async (page) => {
    const STATE = `(() => { const shown = [...${THEME}.querySelectorAll("svg")].filter((svg) => svg.checkVisibility()); return { label: ${THEME}.getAttribute("aria-label"), icons: shown.map((svg) => svg.getAttribute("class")), width: shown[0].getBoundingClientRect().width } })()`
    const seen = []
    for (let i = 0; i < 4; i++) {
      seen.push(await page.eval(STATE))
      await page.click("button.theme")
    }
    assert.deepEqual(seen.map((s) => [s.label, s.icons.join()]), [
      ["Color scheme: System", "system"], ["Color scheme: Light", "light"], ["Color scheme: Dark", "dark"], ["Color scheme: System", "system"],
    ])
    assert.ok(seen.every((s) => s.width >= 16), "icons must be visible")
    assert.equal(await page.eval(`${THEME}.title`), "Color scheme: Light")
  }))

  it("overrides the system scheme, including code highlighting, and remembers it across pages", () => withTheme(DESKTOP, "ksp/", async (page) => {
    await emulate(page, "light")
    assert.equal(await page.eval(BACKGROUND), LIGHT)
    const lightKeyword = await page.eval(KEYWORD)

    await choose(page, "dark")
    assert.equal(await page.eval(BACKGROUND), DARK)
    await page.waitFor(`${KEYWORD} !== ${JSON.stringify(lightKeyword)}`)

    await page.goto(site.baseUrl + "dsl/")
    assert.equal(await page.eval(BACKGROUND), DARK)
    assert.equal(await page.eval(`${THEME}.dataset.choice`), "dark")
  }))

  it("stays light on a dark system when light is chosen", () => withTheme(DESKTOP, "ksp/", async (page) => {
    await emulate(page, "dark")
    assert.equal(await page.eval(BACKGROUND), DARK)
    await choose(page, "light")
    assert.equal(await page.eval(BACKGROUND), LIGHT)
    assert.equal(await page.eval(`matchMedia(document.getElementById("syntax-dark").media).matches`), false)
  }))

  it("follows the system again after choosing system", () => withTheme(DESKTOP, "ksp/", async (page) => {
    await emulate(page, "dark")
    await choose(page, "light")
    await choose(page, "system")
    assert.equal(await page.eval(BACKGROUND), DARK)
    assert.equal(await page.eval(`localStorage.getItem("theme")`), null)
    await emulate(page, "light")
    assert.equal(await page.eval(BACKGROUND), LIGHT)
  }))

  it("ignores an unknown stored value", () => withTheme(DESKTOP, "ksp/", async (page) => {
    await page.eval(`localStorage.setItem("theme", "sepia")`)
    await page.goto(site.baseUrl + "ksp/")
    assert.equal(await page.eval(`${THEME}.dataset.choice`), "system")
  }))

  for (const [name, viewport] of [["desktop", DESKTOP], ["mobile", MOBILE]]) {
    it(`themes the search field and its results on ${name}`, () => withTheme(viewport, "ksp/", async (page) => {
      await emulate(page, "light")
      await page.waitFor(SEARCH_INPUT)
      await choose(page, "dark")
      await page.eval(`deep("pagefind-searchbox input")[0].focus()`)
      await page.type("polymorphism")
      await page.waitFor(`(() => { const l = ${SEARCH_RESULTS}; return l.length && l })()`)
      const r = await page.eval(`(() => { const color = (sel) => getComputedStyle(deep(sel)[0]).backgroundColor
        return { input: color("pagefind-searchbox input"), results: color("pagefind-searchbox .pf-searchbox-dropdown") } })()`)
      assert.deepEqual(r, { input: DARK, results: DARK })
    }))
  }

  it("redraws mermaid diagrams in the new scheme", () => withTheme(DESKTOP, "architecture/", async (page) => {
    // Without its generated id, the stylesheet of a diagram only differs by theme.
    const STYLE = `document.querySelector("pre.mermaid svg style")?.textContent.replace(/mermaid-\\d+/g, "")`
    await emulate(page, "light")
    await page.goto(site.baseUrl + "architecture/")
    const light = await page.waitFor(STYLE)

    await choose(page, "dark")
    const dark = await page.waitFor(`(() => { const style = ${STYLE}; return style !== ${JSON.stringify(light)} && style })()`)

    await choose(page, "system")
    await page.waitFor(`${STYLE} === ${JSON.stringify(light)}`)
    await emulate(page, "dark")
    await page.waitFor(`${STYLE} === ${JSON.stringify(dark)}`)
    assert.deepEqual(page.problems, [])
  }))

  for (const [name, viewport] of [["desktop", DESKTOP], ["mobile", MOBILE]]) {
    it(`fits in the header next to the search field on ${name}`, () => withTheme(viewport, "ksp/", async (page) => {
      await page.waitFor(SEARCH_INPUT)
      const r = await page.eval(`(() => { const rect = (el) => el.getBoundingClientRect(), select = rect(${THEME}), box = rect(document.querySelector("pagefind-searchbox"))
        return { sameRow: select.top < box.bottom && box.top < select.bottom, after: select.left >= box.right, right: select.right, overflow: document.documentElement.scrollWidth > innerWidth, tall: rect(document.querySelector("header")).height } })()`)
      assert.ok(r.sameRow && r.after, `selector must sit right of the search field: ${JSON.stringify(r)}`)
      assert.equal(r.overflow, false)
      assert.ok(r.tall <= 110, `header grew to ${r.tall}px`)
    }))
  }
})

describe("sidebar", () => {
  it("scrolls in one column on a short window", () => withPage({ width: 1280, height: 500 }, "ksp/", async (page) => {
    const lefts = await page.eval(`[...new Set([...document.querySelectorAll("#site-nav a")].map((a) => a.getBoundingClientRect().left))]`)
    assert.equal(lefts.length, 1, `menu links span several columns: ${lefts}`)
  }))
})

describe("tables", () => {
  it("keep their prose column readable on mobile", () => withPage(MOBILE, "ksp/", async (page) => {
    const width = await page.eval(`document.querySelector("main table td:last-child").getBoundingClientRect().width`)
    assert.ok(width >= 200, `last column is ${width}px wide`)
    assert.equal(await page.eval(`document.documentElement.scrollWidth > innerWidth`), false, "the table must scroll inside its wrapper")
  }))
})

describe("code blocks", () => {
  it("get a copy button that copies the raw code, except mermaid diagrams", () => withPage(DESKTOP, "ksp/", async (page) => {
    const r = await page.eval(`(() => { const pres = [...document.querySelectorAll("pre.chroma")]
      return { count: pres.length, withButton: pres.filter((p) => p.closest(".code")?.querySelector(":scope > button.copy")).length } })()`)
    assert.ok(r.count > 0 && r.withButton === r.count, `every pre.chroma needs a copy button: ${JSON.stringify(r)}`)
    assert.ok(await page.eval(`[...document.querySelectorAll(".code > .lang")].some((l) => l.textContent === "kotlin")`), "a kotlin block shows its language label")
    // Headless Chrome has no clipboard permission, so capture what would be written.
    const result = await page.eval(`(async () => {
      let written; navigator.clipboard.writeText = async (t) => { written = t }
      const button = document.querySelector("button.copy"); button.click()
      await new Promise((r) => setTimeout(r, 50))
      return { label: button.textContent, ok: written === button.parentElement.querySelector("pre").textContent && written.length > 0 } })()`)
    assert.deepEqual(result, { label: "Copied", ok: true })
  }))

  it("report a failed copy", () => withPage(DESKTOP, "ksp/", async (page) => {
    await page.eval(`navigator.clipboard.writeText = async () => { throw new Error("denied") }; document.querySelector("button.copy").click()`)
    await page.waitFor(`document.querySelector("button.copy").textContent === "Copy failed"`)
    assert.deepEqual(page.problems, [], "the rejection must be handled")
  }))

  it("leave mermaid diagrams without one", () => withPage(DESKTOP, "architecture/", async (page) => {
    const r = await page.eval(`({ diagrams: document.querySelectorAll("pre.mermaid").length,
      buttons: [...document.querySelectorAll("pre.mermaid")].filter((p) => p.closest(".code")).length })`)
    assert.ok(r.diagrams > 0)
    assert.equal(r.buttons, 0)
    assert.equal(await page.eval(`document.querySelectorAll(".lang").length`), 0)
  }))
})

describe("table of contents", () => {
  const TOC = `(() => { const rect = (el) => el.getBoundingClientRect(), toc = rect(document.querySelector(".toc")), main = rect(document.getElementById("content"))
    return { tocLeft: toc.left, tocRight: toc.right, tocTop: toc.top, mainRight: main.right, mainLeft: main.left, headerBottom: rect(document.querySelector("header")).bottom, viewport: innerWidth } })()`
  const ACTIVE = `[...document.querySelectorAll(".toc a[aria-current]")].map((a) => a.hash)`

  it("sits in a right column beside the content and stays in view while scrolling on desktop", () => withPage(DESKTOP, "ksp/", async (page) => {
    const before = await page.eval(TOC)
    assert.ok(before.tocLeft >= before.mainRight, `TOC (${before.tocLeft}) must not overlap the content (${before.mainRight})`)
    assert.ok(before.tocRight <= before.viewport, "TOC must fit in the viewport")
    assert.ok(before.tocTop >= before.headerBottom, "TOC must sit below the header")
    await page.eval(`scrollTo(0, 1500)`)
    assert.deepEqual(await page.eval(TOC), before, "TOC must not move when the page scrolls")
  }))

  it("stays above the content on mobile", () => withPage(MOBILE, "ksp/", async (page) => {
    const r = await page.eval(`(() => ({ position: getComputedStyle(document.querySelector(".toc")).position,
      tocBottom: document.querySelector(".toc").getBoundingClientRect().bottom, contentTop: document.querySelector("main > p").getBoundingClientRect().top }))()`)
    assert.notEqual(r.position, "fixed")
    assert.ok(r.tocBottom <= r.contentTop, "TOC must precede the content")
  }))

  it("highlights the section being read as the page scrolls", () => withPage(DESKTOP, "ksp/", async (page) => {
    const hashes = await page.eval(`[...document.querySelectorAll(".toc a")].map((a) => a.hash)`)
    assert.ok(hashes.length >= 3, `expected several TOC links, got ${hashes.length}`)
    for (const hash of [hashes[0], hashes[2], hashes[1]]) {
      await page.eval(`location.hash = ${JSON.stringify(hash)}`)
      await page.waitFor(`${ACTIVE}.join() === ${JSON.stringify(hash)}`)
    }
  }))

  it("highlights the last section at the bottom of the page", () => withPage(DESKTOP, "ksp/", async (page) => {
    const last = await page.eval(`[...document.querySelectorAll(".toc a")].pop().hash`)
    await page.eval(`scrollTo(0, document.documentElement.scrollHeight)`)
    await page.waitFor(`${ACTIVE}.join() === ${JSON.stringify(last)}`)
  }))

  for (const [name, viewport] of [["desktop", DESKTOP], ["mobile", MOBILE]]) {
    it(`follows a clicked link to a heading clear of the header and highlights it on ${name}`, async () => {
      // configuration/ ends with sections too short to reach the top of the viewport.
      for (const path of ["ksp/", "configuration/"]) {
        await withPage(viewport, path, async (page) => {
          for (const hash of await page.eval(`[...document.querySelectorAll(".toc a")].map((a) => a.hash)`)) {
            await page.eval(`document.querySelector('.toc a[href="${hash}"]').click()`)
            await page.waitFor(`${ACTIVE}.join() === ${JSON.stringify(hash)}`, { timeout: 2000 })
            const r = await page.eval(`({ top: document.querySelector(${JSON.stringify(hash)}).getBoundingClientRect().top, header: document.querySelector("header").getBoundingClientRect().bottom })`)
            assert.ok(r.top >= r.header, `${path}${hash}: heading at ${r.top} is hidden by the header ending at ${r.header}`)
          }
        })
      }
    })
  }

  it("goes back to following the scroll position once the reader scrolls on", () => withPage(DESKTOP, "configuration/", async (page) => {
    const hashes = await page.eval(`[...document.querySelectorAll(".toc a")].map((a) => a.hash)`)
    await page.eval(`location.hash = ${JSON.stringify(hashes.at(-2))}`)
    await page.waitFor(`${ACTIVE}.join() === ${JSON.stringify(hashes.at(-2))}`)
    await page.eval(`scrollTo(0, 0)`) // no input event, as when dragging the scrollbar
    await page.waitFor(`${ACTIVE}.length === 0`)
  }))

  it("highlights a linked section near the page end when opened directly", () => withPage(DESKTOP, "configuration/#precedence-rules", async (page) => {
    await page.waitFor(`${ACTIVE}.join() === "#precedence-rules"`)
  }))

  it("highlights nothing on a page that does not scroll", () => withPage({ width: 1280, height: 30000 }, "reflection/", async (page) => {
    assert.equal(await page.eval(`document.documentElement.scrollHeight > innerHeight`), false, "the page must fit the viewport")
    assert.deepEqual(await page.eval(ACTIVE), [])
  }))

  it("reopens when a window collapsed at phone width grows to the column", () => withPage({ width: 700, height: 800, mobile: true }, "ksp/", async (page) => {
    assert.equal(await page.eval(`document.querySelector(".toc").open`), false)
    await page.send("Emulation.setDeviceMetricsOverride", { width: 1280, height: 800, deviceScaleFactor: 1, mobile: false })
    await page.waitFor(`document.querySelector(".toc a").checkVisibility()`)
  }))

  for (const width of [1200, 1280, 1920]) {
    it(`keeps a clear gap to the text without drifting away at ${width}px`, () => withPage({ width, height: 900 }, "ksp/", async (page) => {
      const gap = await page.eval(`(() => { const main = document.getElementById("content")
        return document.querySelector(".toc nav").getBoundingClientRect().left - (main.getBoundingClientRect().right - parseFloat(getComputedStyle(main).paddingRight)) })()`)
      assert.ok(gap >= 48 && gap <= 80, `gap between the text and the TOC: ${gap}px`)
    }))
  }

  it("reserves no column on a page without one", () => withPage(DESKTOP, "", async (page) => {
    assert.equal(await page.eval(`document.getElementById("content").getBoundingClientRect().width`), 860)
  }))

  it("has no dead keyboard stop in the desktop column", () => withPage(DESKTOP, "ksp/", async (page) => {
    assert.equal(await page.eval(`shown(document.querySelector(".toc summary"))`), false)
    assert.equal(await page.eval(`shown(document.querySelector(".toc a"))`), true)
  }))

  it("starts collapsed with comfortable tap targets on mobile", () => withPage(MOBILE, "ksp/", async (page) => {
    assert.equal(await page.eval(`document.querySelector(".toc").open`), false)
    await page.click(".toc summary")
    assert.equal(await page.eval(`document.querySelector(".toc").open`), true)
    const height = await page.eval(`Math.min(...[...document.querySelectorAll(".toc a")].map((a) => a.getBoundingClientRect().height))`)
    assert.ok(height >= 36, `TOC links are ${height}px tall`)
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
