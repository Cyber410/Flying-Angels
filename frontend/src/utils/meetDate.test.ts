import { expect, it } from "vitest";
import { formatMeetDates } from "./meetDate";

it("keeps single-day dates compact and displays both days for weekend meets",()=>{
  expect(formatMeetDates("2026-10-17","2026-10-17")).toBe("Oct 17, 2026");
  expect(formatMeetDates("2026-10-17","2026-10-18")).toBe("Oct 17, 2026 – Oct 18, 2026");
});
