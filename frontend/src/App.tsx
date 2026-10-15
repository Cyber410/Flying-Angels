import {
  BrowserRouter,
  Route,
  Routes,
} from "react-router-dom";

import AthleteDirectoryPage from "./pages/AthleteDirectoryPage";
import AthleteProfilePage from "./pages/AthleteProfilePage";

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
      </Routes>
    </BrowserRouter>
  );
}

export default App;