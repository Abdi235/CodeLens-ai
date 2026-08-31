import { describe, expect, it } from 'vitest'
import { shouldPollResults, statusLabel } from './analysisStatus'

describe('analysisStatus', () => {
  it('maps known statuses', () => {
    expect(statusLabel('QUEUED')).toBe('Queued')
    expect(statusLabel('COMPLETED')).toBe('Completed')
  })

  it('polls while job is in flight', () => {
    expect(shouldPollResults('PROCESSING')).toBe(true)
    expect(shouldPollResults('COMPLETED')).toBe(false)
  })
})
