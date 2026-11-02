import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ImportAthletesPage from "./ImportAthletesPage";
import { confirmAthleteImport, previewAthleteImport } from "../services/athleteImportService";
import type { ImportPreview } from "../types/athleteImport";

vi.mock("../services/athleteImportService");
const preview: ImportPreview={filename:"sample.csv",importId:"job-1",validCount:1,duplicateCount:1,errorCount:1,rows:[
  {rowNumber:2,firstName:"New",lastName:"Runner",dateOfBirth:"2012-01-01",gender:"Female",flyStatus:"Active",flyaStatus:"",note:"",seedMarks:[],status:"VALID",errors:[],warnings:[]},
  {rowNumber:3,firstName:"Existing",lastName:"Runner",dateOfBirth:"2012-01-01",gender:"Male",flyStatus:"",flyaStatus:"",note:"",seedMarks:[],status:"DUPLICATE",errors:[],warnings:["Already exists."]},
  {rowNumber:4,firstName:"",lastName:"Runner",dateOfBirth:"",gender:"",flyStatus:"",flyaStatus:"",note:"",seedMarks:[],status:"ERROR",errors:["First name is required."],warnings:[]},
]};
beforeEach(()=>{vi.resetAllMocks();vi.mocked(previewAthleteImport).mockResolvedValue(preview);});
function renderPage(){render(<MemoryRouter><ImportAthletesPage /></MemoryRouter>);}
async function upload(){const user=userEvent.setup();renderPage();await user.upload(screen.getByLabelText("Choose file"),new File(["sample"],"sample.csv",{type:"text/csv"}));await user.click(screen.getByRole("button",{name:"Preview file"}));await screen.findByRole("region",{name:"Import preview"});return user;}

describe("Import Athletes",()=>{
  it("previews without confirming or writing records",async()=>{
    await upload();expect(previewAthleteImport).toHaveBeenCalledOnce();expect(confirmAthleteImport).not.toHaveBeenCalled();
    expect(screen.getByRole("checkbox",{name:/Include row 2/})).toBeChecked();
    expect(screen.getByRole("checkbox",{name:/Include row 3/})).toBeDisabled();
    expect(screen.getByRole("checkbox",{name:/Include row 4/})).toBeDisabled();
    expect(screen.getByText("First name is required.")).toBeInTheDocument();
  });
  it("requires an explicit final confirmation and shows actual counts",async()=>{
    const user=await upload();vi.mocked(confirmAthleteImport).mockResolvedValue({importedCount:1,seedResultCount:0,duplicateCount:1,invalidCount:1,notSelectedCount:0,rows:[]});
    await user.click(screen.getByRole("button",{name:"Review 1 selected"}));expect(confirmAthleteImport).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button",{name:"Import 1 athletes"}));
    expect(await screen.findByText("Import complete")).toBeInTheDocument();
    expect(confirmAthleteImport).toHaveBeenCalledWith("job-1",[2]);
    expect(screen.getByText("1 athletes and 0 unlinked seed marks added.")).toBeInTheDocument();
  });
  it("blocks confirmation when all valid rows are deselected",async()=>{
    const user=await upload();await user.click(screen.getByRole("button",{name:"Deselect all"}));
    expect(screen.getByRole("button",{name:"Review 0 selected"})).toBeDisabled();
  });
  it("reports a preview failure and allows another attempt",async()=>{
    vi.mocked(previewAthleteImport).mockRejectedValue(new Error("Invalid headers"));const user=userEvent.setup();renderPage();
    await user.upload(screen.getByLabelText("Choose file"),new File(["data"],"sample.csv"));await user.click(screen.getByRole("button",{name:"Preview file"}));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid headers");expect(confirmAthleteImport).not.toHaveBeenCalled();
  });
});
