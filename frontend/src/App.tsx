import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { PlayerProvider } from './context/PlayerContext';
import { Navbar } from './components/Navbar';
import { Footer } from './components/Footer';
import { AuthModal } from './components/AuthModal';
import { MiniPlayer } from './components/MiniPlayer';
import { GuestUpgradeModal } from './components/GuestUpgradeModal';

import { CatalogPage } from './pages/CatalogPage';
import { AlbumDetailPage } from './pages/AlbumDetailPage';
import { ListsPage } from './pages/ListsPage';
import { ListDetailPage } from './pages/ListDetailPage';
import { SearchImportPage } from './pages/SearchImportPage';
import { ArtistPage } from './pages/ArtistPage';
import { ProfilePage } from './pages/ProfilePage';
import { MessagesPage } from './pages/MessagesPage';
import { ActivityFeedPage } from './pages/ActivityFeedPage';
import { SettingsPage } from './pages/SettingsPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';

import './App.css';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <PlayerProvider>
          <div className="aficio-app-layout">
            <Navbar />
            <main className="aficio-main-content">
              <Routes>
                <Route path="/" element={<CatalogPage />} />
                <Route path="/catalog" element={<CatalogPage />} />
                <Route path="/album/:id" element={<AlbumDetailPage />} />
                <Route path="/artist/:id" element={<ArtistPage />} />
                <Route path="/lists" element={<ListsPage />} />
                <Route path="/lists/:id" element={<ListDetailPage />} />
                <Route path="/search" element={<SearchImportPage />} />
                <Route path="/u/:username" element={<ProfilePage />} />
                <Route path="/settings" element={<SettingsPage />} />
                <Route path="/messages" element={<MessagesPage />} />
                <Route path="/feed" element={<ActivityFeedPage />} />
                <Route path="/verify-email" element={<VerifyEmailPage />} />
              </Routes>
            </main>
            <Footer />
            <AuthModal />
            <GuestUpgradeModal />
            <MiniPlayer />
          </div>
        </PlayerProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
