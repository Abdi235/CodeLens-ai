export function statusLabel(status: string): string {
  switch (status) {
    case 'QUEUED':
      return 'Queued'
    case 'PROCESSING':
      return 'Processing'
    case 'COMPLETED':
      return 'Completed'
    case 'FAILED':
      return 'Failed'
    default:
      return status
  }
}

export function shouldPollResults(status: string | undefined): boolean {
  return status === 'QUEUED' || status === 'PROCESSING'
}
