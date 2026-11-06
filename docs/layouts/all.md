{{- /* The markdown behind "Copy page" and "View as Markdown": the source without knit markers, its links made absolute as they would break outside the repository. */ -}}
{{- $markdown := .RawContent | replaceRE `<!---[\s\S]*?-->[ \t]*\n?` "" -}}
{{- $markdown = $markdown | replaceRE `\]\(\.\./([^)]*)\)` (printf "](%s/blob/main/$1)" site.Params.repo) -}}
{{- $markdown = $markdown | replaceRE `\]\(([\w-]+)\.md(#[^)]*)?\)` (printf "](%s$1/index.md$2)" ("" | absURL)) -}}
{{- strings.TrimSpace $markdown -}}
