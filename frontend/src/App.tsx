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

function App() {
  return (
    <BrowserRouter>
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
        {/* /import is reserved for the team's Import Athletes page. */}
      </Routes>
    </BrowserRouter>
  );
}

export default App;
