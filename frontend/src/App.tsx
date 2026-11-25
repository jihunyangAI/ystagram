import { BrowserRouter, Route, Routes } from 'react-router'
import { AppLayout } from './components/layout/AppLayout'
import { LabSwitcher } from './components/lab/LabSwitcher'
import { Toaster } from './components/Toast'
import { HomePage } from './pages/HomePage'
import { LabPage } from './pages/LabPage'
import { LabAlgorithmPage } from './pages/lab/LabAlgorithmPage'
import { LabEventsPage } from './pages/lab/LabEventsPage'
import { LabFlagsPage } from './pages/lab/LabFlagsPage'
import { LabSimulatorPage } from './pages/lab/LabSimulatorPage'
import { InsightsPage, PostInsightsPage } from './pages/insights/InsightsPage'
import { AlgorithmPage } from './pages/settings/AlgorithmPage'
import { ScheduledPage } from './pages/settings/ScheduledPage'
import { ReportsPage, TwoFactorPage } from './pages/settings/SecurityPages'
import { LabModerationPage } from './pages/lab/LabModerationPage'
import { MapPage } from './pages/direct/MapPage'
import { LabAdsPage } from './pages/lab/LabAdsPage'
import { LiveRoomPage, LiveStartPage } from './pages/live/LivePage'
import { ManageProductsPage, MyShopPage, ProductPage } from './pages/shop/ShopPages'
import { SubscriptionsPage } from './pages/shop/SubscriptionsPage'
import { LabCommercePage } from './pages/lab/LabCommercePage'
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
import { ExplorePage } from './pages/search/ExplorePage'
import { CreateReelPage } from './pages/reels/CreateReelPage'
import { ReelsPage } from './pages/reels/ReelsPage'
import { SettingsPage } from './pages/settings/SettingsPage'
import { PickListPage, RelationListPage } from './pages/settings/RelationPages'
import { CreateStoryPage } from './pages/story/CreateStoryPage'
import { StoryArchivePage } from './pages/story/StoryArchivePage'
import { PostArchivePage, RecentlyDeletedPage } from './pages/post/PostArchivePages'
import { AudioPage } from './pages/reels/AudioPage'
import { LoginActivityPage, PasswordResetPage, RemoveAccountPage } from './pages/settings/AccountCenterPages'
import {
  CommentControlsPage,
  DraftsPage,
  HiddenWordsPage,
  NotificationSettingsPage,
  SensitiveContentPage,
  TimeManagementPage,
} from './pages/settings/SafetyPages'
import { StoryViewerPage } from './pages/story/StoryViewerPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/accounts/login" element={<LoginPage />} />
        <Route path="/accounts/signup" element={<SignupPage />} />
        <Route path="/accounts/password/reset" element={<PasswordResetPage />} />

        <Route element={<AppLayout />}>
          <Route index element={<HomePage />} />
          <Route path="reels" element={<ReelsPage />} />
          <Route path="reels/:shortcode" element={<ReelsPage />} />
          <Route path="create/reel" element={<CreateReelPage />} />
          <Route path="reels/:shortcode/remix" element={<CreateReelPage />} />
          <Route path="direct/inbox" element={<DirectPage />} />
          <Route path="direct/t/:id" element={<DirectPage />} />
          <Route path="direct/channel/:channelId" element={<DirectPage />} />
          <Route path="direct/blend/:blendId" element={<DirectPage />} />
          <Route path="direct/map" element={<MapPage />} />
          <Route path="explore" element={<ExplorePage />} />
          <Route path="explore/search" element={<SearchPage />} />
          <Route path="explore/tags/:tag" element={<TagPage />} />
          <Route path="explore/locations/:id" element={<PlacePage />} />
          <Route path="notifications" element={<ActivityPage />} />
          <Route path="notifications/requests" element={<FollowRequestsPage />} />
          <Route path="stories/:username" element={<StoryViewerPage />} />
          <Route path="stories/highlights/:id" element={<StoryViewerPage />} />
          <Route path="archive/stories" element={<StoryArchivePage />} />
          <Route path="archive/posts" element={<PostArchivePage />} />
          <Route path="audio/:id" element={<AudioPage />} />
          <Route path="accounts/login-activity" element={<LoginActivityPage />} />
          <Route path="accounts/remove" element={<RemoveAccountPage />} />
          <Route path="accounts/hidden-words" element={<HiddenWordsPage />} />
          <Route path="accounts/comments" element={<CommentControlsPage />} />
          <Route path="accounts/notifications" element={<NotificationSettingsPage />} />
          <Route path="accounts/sensitive" element={<SensitiveContentPage />} />
          <Route path="accounts/time" element={<TimeManagementPage />} />
          <Route path="accounts/drafts" element={<DraftsPage />} />
          <Route path="accounts/recently-deleted" element={<RecentlyDeletedPage />} />
          <Route path="create" element={<CreatePostPage />} />
          <Route path="create/story" element={<CreateStoryPage />} />
          <Route path="p/:shortcode" element={<PostPage />} />
          <Route path="accounts/edit" element={<EditProfilePage />} />
          <Route path="accounts/settings" element={<SettingsPage />} />
          <Route path="accounts/blocked" element={<RelationListPage kind="blocked" />} />
          <Route path="accounts/restricted" element={<RelationListPage kind="restricted" />} />
          <Route path="accounts/muted" element={<RelationListPage kind="muted" />} />
          <Route path="accounts/close-friends" element={<PickListPage kind="close-friends" />} />
          <Route path="accounts/favorites" element={<PickListPage kind="favorites" />} />
          <Route path="lab" element={<LabPage />} />
          <Route path="lab/algorithm" element={<LabAlgorithmPage />} />
          <Route path="lab/events" element={<LabEventsPage />} />
          <Route path="lab/simulator" element={<LabSimulatorPage />} />
          <Route path="lab/flags" element={<LabFlagsPage />} />
          <Route path="insights" element={<InsightsPage />} />
          <Route path="p/:shortcode/insights" element={<PostInsightsPage />} />
          <Route path="accounts/algorithm" element={<AlgorithmPage />} />
          <Route path="accounts/scheduled" element={<ScheduledPage />} />
          <Route path="accounts/two-factor" element={<TwoFactorPage />} />
          <Route path="accounts/reports" element={<ReportsPage />} />
          <Route path="lab/moderation" element={<LabModerationPage />} />
          <Route path="lab/ads" element={<LabAdsPage />} />
          <Route path="lab/commerce" element={<LabCommercePage />} />
          <Route path="live/new" element={<LiveStartPage />} />
          <Route path="shop/p/:id" element={<ProductPage />} />
          <Route path="shop/manage" element={<ManageProductsPage />} />
          <Route path="accounts/shop" element={<MyShopPage />} />
          <Route path="accounts/subscriptions" element={<SubscriptionsPage />} />
          <Route path="live/:id" element={<LiveRoomPage />} />
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
