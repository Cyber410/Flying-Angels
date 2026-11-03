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

function App() {
  return (
    <BrowserRouter>
      <div className="app-shell">
        <div className="app-content">
      <Routes>
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
        <Route path="/import" element={<ImportAthletesPage />} />
      </Routes>
        </div>
        <Footer />
      </div>
    </BrowserRouter>
  );
}

export default App;
