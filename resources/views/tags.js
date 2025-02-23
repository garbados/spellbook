function tags (doc) {
  if (doc.type === 'entry') {
    for (const tag of doc.tags) {
      emit(tag)
    }
  }
}
