# Историческая проверка v5 на рабочем ПК

Этот документ описывает предыдущую версию. Актуальная проверка v6 находится в docs/QA.md.

# Work PC review

Use the single CopiMine_Prototype_v5.html: all images, scripts and CSS are inline. No remote PC, API key, Node, Python or Figma required. Ordinary offline viewing makes no external asset requests. Explicit Discord/outbound links need internet and may be blocked at work; that does not break local pages.
The optional START.bat path needs Python 3.10+. It does not search the home computer by default. Old home paths are context only; no credentials or connection batch file are shipped.
Original optional paths: D:\.minecraft\versions\CopiMine\mods and its journeymap directory. Do not treat that minimap cache as the full published world. This package uses the supplied world-region archive instead.
Review examples: examples/inventory.json; examples/events.jsonl; examples/server.log; examples/recipe.json. These contain artificial Demo users, not private server records.
Registration and remember-me are local scenarios. Browser file storage policies can prevent persistence; the UI says when it is using temporary memory. Export non-secret review state before closing if needed. The store is isolated from v4.
The head image remains a default until you import a PNG or connect the actual backend skin mapping. A head icon is not proof of an authenticated identity. Do not enter real credentials or recipient contacts in this review.
