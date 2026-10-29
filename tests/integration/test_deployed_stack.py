"""Exercise the built frontend -> Nginx -> backend -> MySQL Compose stack."""

import json
import os
import unittest
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen


BASE_URL = os.environ.get("TEST_BASE_URL", "http://localhost:18080").rstrip("/")


def request(method, path, body=None):
    headers = {"Content-Type": "application/json"} if body is not None else {}
    data = json.dumps(body).encode() if body is not None else None
    req = Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        response = urlopen(req, timeout=15)
    except HTTPError as error:
        response = error
    with response:
        raw = response.read()
        content_type = response.headers.get("Content-Type", "")
        parsed = json.loads(raw) if raw and "application/json" in content_type else raw.decode()
        return response.status, parsed


class DeployedStackTests(unittest.TestCase):
    def test_frontend_and_react_route_are_served(self):
        for path in ("/", "/athletes/123"):
            status, html = request("GET", path)
            self.assertEqual(status, 200)
            self.assertIn('id="root"', html)

    def test_athlete_lifecycle_through_frontend_proxy(self):
        marker = "CI-" + uuid.uuid4().hex
        athlete = {
            "firstName": marker,
            "lastName": "Pipeline",
            "dateOfBirth": "2010-01-01",
            "gender": "Female",
            "flyStatus": "In Roster",
            "flyaStatus": "",
            "note": "Created by container integration test",
            "events": [],
        }
        athlete_id = None
        try:
            status, created = request("POST", "/api/athletes", athlete)
            self.assertEqual(status, 201, created)
            athlete_id = created["id"]
            status, profile = request("GET", f"/api/athletes/{athlete_id}")
            self.assertEqual(status, 200)
            self.assertEqual(profile["dateOfBirth"], athlete["dateOfBirth"])
            status, directory = request("GET", f"/api/athletes?search={marker}")
            self.assertEqual(status, 200)
            self.assertEqual([row["id"] for row in directory], [athlete_id])
            athlete["note"] = "Updated by container integration test"
            status, updated = request("PUT", f"/api/athletes/{athlete_id}", athlete)
            self.assertEqual(status, 200, updated)
            status, saved = request("GET", f"/api/athletes/{athlete_id}")
            self.assertEqual(status, 200)
            self.assertEqual(saved["note"], athlete["note"])
            status, body = request("DELETE", f"/api/athletes/{athlete_id}")
            self.assertEqual(status, 204, body)
            status, body = request("GET", f"/api/athletes/{athlete_id}")
            self.assertEqual(status, 404, body)
            athlete_id = None
        finally:
            if athlete_id is not None:
                request("DELETE", f"/api/athletes/{athlete_id}")

    def test_invalid_athlete_is_rejected(self):
        status, body = request("POST", "/api/athletes", {"firstName": "", "lastName": "Pipeline"})
        self.assertEqual(status, 400, body)


if __name__ == "__main__":
    unittest.main(verbosity=2)
