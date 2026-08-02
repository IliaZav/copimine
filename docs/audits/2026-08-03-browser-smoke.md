# CopiMine focused browser smoke — 2026-08-03

This is a focused production smoke, not a replacement for the complete
authenticated Playwright matrix called out by `WEB-28`.

## Environment

- Playwright Chromium.
- `copimine.ru` was resolved to the configured public address `90.188.115.155`
  with the request Host/SNI kept as `copimine.ru`.
- The test used the deployed HTTPS endpoint.

## Results

The following public routes loaded with HTTP `200` and non-empty document
titles and bodies:

`/`, `/index.html`, `/server.html`, `/elections.html`, `/shops.html`,
`/mods.html`, `/signin.html`, and `/register.html`.

The sign-in form rendered with three inputs and three buttons. A deliberately
invalid credential submission reached the backend and returned the expected
HTTP `401`; it did not produce a page error. The run recorded zero
`pageErrors` and zero failed network requests. The browser console contained
only expected `401` responses from anonymous pages probing protected session
endpoints; these were not JavaScript exceptions or broken document resources.

The public smoke also covered the homepage navigation and download links; the
resource pack and modpack downloads returned successfully in the direct HTTPS
smoke documented in the release ledger.

## Boundary

No real user, admin, or Minecraft account credentials were used. Therefore
authenticated cabinet/admin navigation, real-player election interaction, and
the full browser matrix remain manual/hosted integration work. This is why
`WEB-28` remains `Partial` rather than being overstated as fully proven.

Local visual artifacts from this run were saved outside the repository at
`D:\Desktop\release\smoke-20260803-browser\home.png` and
`D:\Desktop\release\smoke-20260803-browser\login.png`.
