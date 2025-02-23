# Spellbook

![Build and Test](https://github.com/garbados/spellbook/actions/workflows/tests.yaml/badge.svg)

A diary. Stores everything in your browser. Use [here](https://garbados.github.io/spellbook).

Spellbook is built with:

- [PouchDB](https://pouchdb.com/)
- [ClojureScript](https://clojurescript.org/) via [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html)
- [Alchemist](https://garbados.github.io/html-alchemist/)

Features include:

- Markup text with [Markdown](https://www.markdownguide.org/).
- Discover and index entries with tags.
- Group entries by year and month, for perusing the archives.
- Find entries whose text includes a search query.

## Development

To hack on the guts, first you will need the source and its dependencies:

```sh
$ git clone git@github.com:garbados/spellbook.git
$ cd spellbook
$ npm i
```

Then you can run the tests:

```sh
$ npm test
```

You can also run a dev server which rebuilds the web app whenever its constituent files change. You will need to serve these assets with a separate process:

```sh
$ npm run dev
# in another terminal
$ npm run serve
```

## Architecture

Spellbook demonstrates an [offline first](https://offlinefirst.org/) pattern in which all of the application's logic is contained within code that runs client-side, so the client is never dependent on the server except when first loading the app's web page. Database entries and indices are stored in IndexedDB with PouchDB. Entries include whole [EDN](https://github.com/edn-format/edn#edn) serializations, allowing native Clojure data types to remain fully intact even when deserialized from JSON.

Spellbook is written in ClojureScript with minor interop with JavaScript libraries. I chose Clojure because it's fun, with many efficiencies folded into its declarative idioms. For the UI, I used Alchemist, as it is efficient and expressive.

I did things this way because I believe it's the right way to service user needs, such as a diary. User data should belong on user hardware first, with the possibility of replication to backup servers. Developers like myself should never have to busy ourselves with questions about how to store user data except to consider the schemas being stored; to respect the discretion of users, we should never have even the opportunity to snoop.

As of February 2025, I have not found a suitable replication solution to apply through this ethos. Stay tuned.

## License

[Apache-2.0](https://opensource.org/license/apache-2-0/)
