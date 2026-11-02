import { useNavigate } from 'react-router-dom'
import { HomeDashboard } from '../apps/HomeDashboard'

export function HomePage() {
  const navigate = useNavigate()
  return (
    <HomeDashboard
      onOpenInbox={(type, overdue) => navigate(`/inbox?type=${type}${overdue ? '&overdue=1' : ''}`)}
      onOpenForm={(path) => navigate(path)}
    />
  )
}
