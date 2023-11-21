# Spellbook

![Build and Test](https://github.com/garbados/spellbook/actions/workflows/tests.yaml/badge.svg)

A diary. Stores everything in your browser. Use [here](https://garbados.github.io/spellbook).

Spellbook is built with:

- [PouchDB](https://pouchdb.com/)
- [ClojureScript](https://clojurescript.org/) via [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html)
- [React](https://react.dev/) via [Reagent](https://reagent-project.github.io/)

Features include:

- Markup text with [Markdown](https://www.markdownguide.org/).
- Discover and index entries with tags.
- Group entries by year and month, for perusing the archives.
- Find entries whose text includes a search query.

Roadmap:

- [ ] Encryption with [crypto-pouch](https://github.com/calvinmetcalf/crypto-pouch).
- [ ] Multi-device sync with [CouchDB](https://couchdb.apache.org/).

## License

[Apache-2.0](https://opensource.org/license/apache-2-0/)
