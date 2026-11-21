import { BrowserRouter, Route, Routes } from 'react-router'
import { Clapperboard, Compass, Heart, Search, Send } from 'lucide-react'
import { AppLayout } from './components/layout/AppLayout'
import { LabSwitcher } from './components/lab/LabSwitcher'
import { Toaster } from './components/Toast'
import { ComingSoonPage } from './pages/ComingSoonPage'
import { HomePage } from './pages/HomePage'
import { LabPage } from './pages/LabPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ProfilePage } from './pages/ProfilePage'
import { LoginPage } from './pages/auth/LoginPage'
import { SignupPage } from './pages/auth/SignupPage'
import { EditProfilePage } from './pages/account/EditProfilePage'
import { CreatePostPage } from './pages/post/CreatePostPage'
import { PostPage } from './pages/post/PostPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/accounts/login" element={<LoginPage />} />
        <Route path="/accounts/signup" element={<SignupPage />} />

        <Route element={<AppLayout />}>
          <Route index element={<HomePage />} />
          <Route path="reels" element={<ComingSoonPage title="릴스" stage="M3" icon={Clapperboard} />} />
          <Route path="direct/inbox" element={<ComingSoonPage title="메시지" stage="M2" icon={Send} />} />
          <Route path="explore" element={<ComingSoonPage title="탐색" stage="M3" icon={Compass} />} />
          <Route path="explore/search" element={<ComingSoonPage title="검색" stage="M2" icon={Search} />} />
          <Route path="notifications" element={<ComingSoonPage title="알림" stage="M2" icon={Heart} />} />
          <Route path="create" element={<CreatePostPage />} />
          <Route path="p/:shortcode" element={<PostPage />} />
          <Route path="accounts/edit" element={<EditProfilePage />} />
          <Route path="lab" element={<LabPage />} />
          <Route path=":username" element={<ProfilePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>

      <LabSwitcher />
      <Toaster />
    </BrowserRouter>
  )
}
