# GITBOOK Interactive EPUB packages

Interactive examples are prepared by the GITBOOK publishing/catalog team and shipped inside the book file. The reader has no editor and never asks readers to add HTML, CSS, JavaScript, or ZIP files.

## EPUB package convention

A publisher includes each example as a self-contained folder inside the EPUB. Keep the HTML entry point and all CSS, JavaScript, fonts, and images used by that example in the same folder tree.

Example:

```text
book.epub
├── META-INF/container.xml
└── OEBPS/
    ├── content.opf
    ├── chapter-01.xhtml
    └── interactive/
        └── login-form/
            ├── index.html
            ├── styles.css
            ├── app.js
            └── images/
                └── illustration.png
```

Declare the custom property prefix on the OPF `package` element (EPUB 3), then add a manifest item for each example. The ID must start with `demo-`; the custom `properties` token tells GITBOOK that this resource is an interactive entry point.

```xml
<package version="3.0" prefix="gitbook: https://gitbook.app/ns/epub#">
  <manifest>
    <item
    id="demo-login-form"
    href="interactive/login-form/index.html"
    media-type="text/html"
    properties="gitbook:interactive" />
  </manifest>
</package>
```

Place a marker exactly where the example belongs in the chapter XHTML. The marker ID is the part after `demo-`.

```html
<p>Now try the form below.</p>
<div
    data-gitbook-demo="login-form"
    data-gitbook-title="Try the login form">
</div>
<p>After submitting, continue with the next section.</p>
```

## Reader behavior

- The marker is parsed as a dedicated reader block and stays in the correct reading order.
- The book download already contains the HTML and its local resources; no separate download is needed.
- GITBOOK extracts only that demo's folder into the app's private cache when the reader reaches that visible block.
- The WebView is created only while the demo block is visible (or its page is the active page). Off-screen demos do not run.
- The demo's WebView has JavaScript enabled for its local interactions, but network requests, external navigation, file/content-provider access, pop-up windows, and native JavaScript bridges are blocked.
- Each demo is size- and file-count-limited during extraction. Keep each demo folder under 48 MiB and below 180 files.
- Use relative paths for local resources. Do not depend on CDNs, remote fonts, APIs, embedded external frames, or remote scripts; those are intentionally unavailable offline.

## Publishing guidance

Only publish EPUBs that have been packaged and reviewed by the GITBOOK team. Interactive examples are content, not executable app plugins: they must not need app permissions, account credentials, or access to private app data. Ordinary EPUBs without these manifest markers continue to work unchanged.
