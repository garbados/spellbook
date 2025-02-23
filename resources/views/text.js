function text (doc) {
  if (doc.type === 'entry') {
    for (const word of doc.text.split(/[ \*\#\>\<"',\-]+/)) {
      emit(word.toLowerCase())
    }
  }
}
