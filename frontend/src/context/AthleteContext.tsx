import {
  createContext,
  useContext,
  useState,
  type ReactNode,
} from "react";

import { athletes as initialAthletes } from "../data/athletes";
import type { Athlete } from "../types/athlete";

type NewAthlete = Omit<Athlete, "id">;

type AthleteContextType = {
  athletes: Athlete[];
  addAthlete: (athlete: NewAthlete) => void;
  updateAthlete: (
    id: number,
    updates: Partial<Athlete>
  ) => void;
  deleteAthlete: (id: number) => void;
};

const AthleteContext = createContext<
  AthleteContextType | undefined
>(undefined);

type AthleteProviderProps = {
  children: ReactNode;
};

export function AthleteProvider({
  children,
}: AthleteProviderProps) {
  const [athletes, setAthletes] =
    useState<Athlete[]>(initialAthletes);

  const addAthlete = (newAthlete: NewAthlete) => {
    const nextId =
      athletes.length > 0
        ? Math.max(...athletes.map((athlete) => athlete.id)) + 1
        : 1;

    const athlete: Athlete = {
      id: nextId,
      ...newAthlete,
    };

    setAthletes((currentAthletes) => [
      ...currentAthletes,
      athlete,
    ]);
  };

  const updateAthlete = (
    id: number,
    updates: Partial<Athlete>
  ) => {
    setAthletes((currentAthletes) =>
      currentAthletes.map((athlete) =>
        athlete.id === id
          ? { ...athlete, ...updates }
          : athlete
      )
    );
  };

  const deleteAthlete = (id: number) => {
    setAthletes((currentAthletes) =>
      currentAthletes.filter(
        (athlete) => athlete.id !== id
      )
    );
  };

  return (
    <AthleteContext.Provider
      value={{
        athletes,
        addAthlete,
        updateAthlete,
        deleteAthlete,
      }}
    >
      {children}
    </AthleteContext.Provider>
  );
}

export function useAthletes() {
  const context = useContext(AthleteContext);

  if (!context) {
    throw new Error(
      "useAthletes must be used inside AthleteProvider"
    );
  }

  return context;
}