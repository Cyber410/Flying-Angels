// Connects application routes with the shared footer.
import {
  BrowserRouter,
  Route,
  Routes,
} from "react-router-dom";

import AthleteDirectoryPage from "./pages/AthleteDirectoryPage";
import AthleteProfilePage from "./pages/AthleteProfilePage";
import MeetsListPage from "./pages/MeetsListPage";
import MeetDetailsPage from "./pages/MeetDetailsPage";
import ResultsPage from "./pages/ResultsPage";
import ImportAthletesPage from "./pages/ImportAthletesPage";
import Footer from "./components/Footer";
import AuthProvider from "./auth/AuthProvider";
import RequireSession from "./auth/RequireSession";
import LoginPage from "./pages/LoginPage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import ActivateAccountPage from "./pages/ActivateAccountPage";
import UsersPage from "./pages/UsersPage";
import AthletePortalPage from "./pages/AthletePortalPage";
import PortalMeetsPage from "./pages/PortalMeetsPage";
import AvailabilityPage from "./pages/AvailabilityPage";
import RankingsPage from "./pages/RankingsPage";
import { Navigate } from "react-router-dom";

// Connect application routes with the shared footer.
function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
      <div className="app-shell">
        <div className="app-content">
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/activate" element={<ActivateAccountPage />} />
        <Route element={<RequireSession permission="READ_STAFF" />}>
        <Route
          path="/"
          element={<AthleteDirectoryPage />}
        />

        <Route
          path="/athletes/:athleteId"
          element={<AthleteProfilePage />}
        />

        <Route path="/meets" element={<MeetsListPage />} />
        <Route path="/meets/demo" element={<MeetDetailsPage sample />} />
        <Route path="/meets/:meetId" element={<MeetDetailsPage />} />
        <Route path="/results" element={<ResultsPage />} />
        <Route path="/rankings" element={<RankingsPage />} />
        <Route path="/availability" element={<AvailabilityPage />} />
        <Route path="/import" element={<ImportAthletesPage />} />
        </Route>
        <Route element={<RequireSession permission="MANAGE_USERS" />}>
          <Route path="/users" element={<UsersPage />} />
        </Route>
        <Route element={<RequireSession athleteOnly />}>
          <Route path="/portal" element={<PortalMeetsPage />} />
          <Route path="/portal/profile" element={<AthletePortalPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
        </div>
        <Footer />
      </div>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
