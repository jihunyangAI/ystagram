import { BrowserRouter, Route, Routes } from 'react-router'
import { Clapperboard, Compass } from 'lucide-react'
import { AppLayout } from './components/layout/AppLayout'
import { LabSwitcher } from './components/lab/LabSwitcher'
import { Toaster } from './components/Toast'
import { ComingSoonPage } from './pages/ComingSoonPage'
import { HomePage } from './pages/HomePage'
import { LabPage } from './pages/LabPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ProfilePage } from './pages/ProfilePage'
import { SavedCollectionPage } from './pages/SavedCollectionPage'
import { ActivityPage } from './pages/activity/ActivityPage'
import { FollowRequestsPage } from './pages/activity/FollowRequestsPage'
import { LoginPage } from './pages/auth/LoginPage'
import { SignupPage } from './pages/auth/SignupPage'
import { EditProfilePage } from './pages/account/EditProfilePage'
import { DirectPage } from './pages/direct/DirectPage'
import { CreatePostPage } from './pages/post/CreatePostPage'
import { PostPage } from './pages/post/PostPage'
import { SearchPage } from './pages/search/SearchPage'
import { PlacePage, TagPage } from './pages/search/TopicPages'
import { CreateStoryPage } from './pages/story/CreateStoryPage'
import { StoryArchivePage } from './pages/story/StoryArchivePage'
import { StoryViewerPage } from './pages/story/StoryViewerPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/accounts/login" element={<LoginPage />} />
        <Route path="/accounts/signup" element={<SignupPage />} />

        <Route element={<AppLayout />}>
          <Route index element={<HomePage />} />
          <Route path="reels" element={<ComingSoonPage title="릴스" stage="M3" icon={Clapperboard} />} />
          <Route path="direct/inbox" element={<DirectPage />} />
          <Route path="direct/t/:id" element={<DirectPage />} />
          <Route path="explore" element={<ComingSoonPage title="탐색" stage="M3" icon={Compass} />} />
          <Route path="explore/search" element={<SearchPage />} />
          <Route path="explore/tags/:tag" element={<TagPage />} />
          <Route path="explore/locations/:id" element={<PlacePage />} />
          <Route path="notifications" element={<ActivityPage />} />
          <Route path="notifications/requests" element={<FollowRequestsPage />} />
          <Route path="stories/:username" element={<StoryViewerPage />} />
          <Route path="archive/stories" element={<StoryArchivePage />} />
          <Route path="create" element={<CreatePostPage />} />
          <Route path="create/story" element={<CreateStoryPage />} />
          <Route path="p/:shortcode" element={<PostPage />} />
          <Route path="accounts/edit" element={<EditProfilePage />} />
          <Route path="lab" element={<LabPage />} />
          <Route path=":username" element={<ProfilePage />} />
          <Route path=":username/saved/:id" element={<SavedCollectionPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>

      <LabSwitcher />
      <Toaster />
    </BrowserRouter>
  )
}
