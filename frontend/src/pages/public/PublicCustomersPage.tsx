import { PublicRecordsPage, type PublicPageMode } from './PublicRecordsPage'

export function PublicCustomersPage({ mode }: { mode?: PublicPageMode }) {
  return <PublicRecordsPage kind="customer" mode={mode} />
}
