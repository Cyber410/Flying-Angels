import {
  BrowserRouter,
  Route,
  Routes,
} from "react-router-dom";

import AthleteDirectoryPage from "./pages/AthleteDirectoryPage";
import AthleteProfilePage from "./pages/AthleteProfilePage";

import { AthleteProvider } from "./context/AthleteContext";

function App() {
  return (
    <AthleteProvider>
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
    </AthleteProvider>
  );
}

export default App;