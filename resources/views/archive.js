function archive (doc) {
  if (doc.type === 'entry') {
    const rawDate = new Date(doc['created-at'])
    const rawDatetime = rawDate.toISOString()
    dateparts = rawDatetime.split('T')
    date = dateparts[0].split('-')
    const rawTime = dateparts[1].split(':')
    year = date[0]
    month = date[1]
    day = date[2]
    hour = rawTime[0]
    minute = rawTime[1]
    second = rawTime[2].split('.').slice(0, -1)
    second = second[0]
    const time = [hour, minute, second]
    const datetime = date.concat(time)
    emit(datetime)
  }
}
