import { findLineIndexForTime } from '../FE/src/composables/useSubtitleSync'
import type { SubtitleLineDto } from '../FE/src/types/api'

function makeLine(id: number, sequence: number, startMs: number, endMs: number): SubtitleLineDto {
  return { id, sequence, startMs, endMs, sourceText: `line ${sequence}`, targetText: `dòng ${sequence}`, tokens: [] }
}

describe('findLineIndexForTime', () => {
  it('returns -1 for an empty array', () => {
    expect(findLineIndexForTime([], 1000)).toBe(-1)
  })

  it('returns -1 when the time is before the first line', () => {
    const lines = [makeLine(1, 0, 1000, 2000), makeLine(2, 1, 2000, 3000)]
    expect(findLineIndexForTime(lines, 500)).toBe(-1)
  })

  it('returns -1 when the time exactly equals the array bound with a single earlier line missing', () => {
    const lines = [makeLine(1, 0, 1000, 2000)]
    expect(findLineIndexForTime(lines, 999)).toBe(-1)
  })

  it('returns the index of the line covering the given time', () => {
    const lines = [makeLine(1, 0, 1000, 2000), makeLine(2, 1, 2000, 3000), makeLine(3, 2, 3000, 4000)]
    expect(findLineIndexForTime(lines, 2500)).toBe(1)
  })

  it('matches exactly on a line boundary (startMs)', () => {
    const lines = [makeLine(1, 0, 1000, 2000), makeLine(2, 1, 2000, 3000)]
    expect(findLineIndexForTime(lines, 2000)).toBe(1)
  })

  it('returns the nearest preceding line when the time falls in a gap between cues', () => {
    const lines = [makeLine(1, 0, 1000, 2000), makeLine(2, 1, 3000, 4000)]
    expect(findLineIndexForTime(lines, 2500)).toBe(0)
  })

  it('returns the last line when the time is after everything', () => {
    const lines = [makeLine(1, 0, 1000, 2000), makeLine(2, 1, 2000, 3000)]
    expect(findLineIndexForTime(lines, 10_000)).toBe(1)
  })

  it('handles a single-line array', () => {
    const lines = [makeLine(1, 0, 1000, 2000)]
    expect(findLineIndexForTime(lines, 1500)).toBe(0)
  })
})
