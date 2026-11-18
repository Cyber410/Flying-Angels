"""Check the built Nginx image with a private echo upstream, without AWS or MySQL."""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
import uuid


IMAGE = os.environ["FRONTEND_IMAGE"]
DOCKER = os.environ.get("DOCKER", "docker")


def docker(*args, check=True):
    return subprocess.run([DOCKER, *args], check=check, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)


class NginxProxyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.prefix = "fa-proxy-" + uuid.uuid4().hex[:12]
        cls.network = cls.prefix + "-network"
        cls.containers = []
        cls.files = tempfile.TemporaryDirectory(prefix=cls.prefix + "-")
        cls.addClassCleanup(cls.cleanup)
        docker("network", "create", "--internal", cls.network)
        echo = Path(cls.files.name) / "echo.conf"
        echo.write_text('''server {
    listen 80;
    location /api/echo {
        default_type application/json;
        return 200 '{"ip":"$http_x_real_ip","xff":"$http_x_forwarded_for","proto":"$http_x_forwarded_proto","host":"$http_x_forwarded_host","forwarded":"$http_forwarded","csrf":"$http_x_csrf_token","cookie":"$http_cookie"}';
    }
}
''')
        cls.echo = cls.prefix + "-echo"
        cls.containers.append(cls.echo)
        docker("run", "-d", "--name", cls.echo, "--network", cls.network,
               "--network-alias", "echo", "--mount",
               f"type=bind,src={echo},dst=/etc/nginx/conf.d/default.conf,readonly",
               "--entrypoint", "nginx", IMAGE, "-g", "daemon off;")
        info = json.loads(docker("inspect", cls.echo).stdout)[0]
        cls.peer_ip = info["NetworkSettings"]["Networks"][cls.network]["IPAddress"]
        cls.start_proxy("direct", "")
        cls.start_proxy("trusted", cls.peer_ip + "/32,2001:db8::/64")
        cls.start_proxy("untrusted", "203.0.113.0/24")
        for _ in range(60):
            if all(cls.fetch(mode, check=False).returncode == 0
                   for mode in ("direct", "trusted", "untrusted")):
                return
            time.sleep(0.2)
        raise RuntimeError("Nginx fixtures did not become ready")

    @classmethod
    def cleanup(cls):
        for name in reversed(cls.containers):
            docker("rm", "-f", name, check=False)
        docker("network", "rm", cls.network, check=False)
        cls.files.cleanup()

    @classmethod
    def start_proxy(cls, mode, cidrs):
        name = cls.prefix + "-" + mode
        cls.containers.append(name)
        docker("run", "-d", "--name", name, "--network", cls.network,
               "--network-alias", mode, "-e", "BACKEND_HOST=echo",
               "-e", "BACKEND_PORT=80", "-e", "NGINX_TRUSTED_PROXY_CIDRS=" + cidrs,
               IMAGE)

    @classmethod
    def fetch(cls, mode, headers=None, path="/api/echo", check=True):
        args = ["exec", cls.echo, "wget", "-qO-", "-T", "3"]
        for name, value in (headers or {}).items():
            args.extend(["--header", f"{name}: {value}"])
        return docker(*args, "http://" + mode + path, check=check)

    def echo_response(self, mode, headers=None):
        return json.loads(self.fetch(mode, headers).stdout)

    def test_direct_clients_cannot_spoof_proxy_metadata(self):
        self.assert_untrusted_headers("direct")

    def test_peers_outside_the_configured_allowlist_are_not_trusted(self):
        self.assert_untrusted_headers("untrusted")

    def assert_untrusted_headers(self, mode):
        data = self.echo_response(mode, {
            "X-Forwarded-For": "198.51.100.25", "X-Real-IP": "198.51.100.26",
            "X-Forwarded-Proto": "https", "X-Forwarded-Host": "attacker.test",
            "Forwarded": "for=198.51.100.27;proto=https",
        })
        self.assertEqual(data["ip"], self.peer_ip)
        self.assertEqual(data["xff"], self.peer_ip)
        self.assertEqual(data["proto"], "http")
        self.assertEqual(data["host"], mode)
        self.assertEqual(data["forwarded"], "")

    def test_trusted_alb_preserves_client_ip_and_https(self):
        data = self.echo_response("trusted", {
            "X-Forwarded-For": "198.51.100.25", "X-Forwarded-Proto": "https",
        })
        self.assertEqual(data["ip"], "198.51.100.25")
        self.assertEqual(data["xff"], "198.51.100.25")
        self.assertEqual(data["proto"], "https")

    def test_alb_appended_address_wins_over_forged_prefix(self):
        data = self.echo_response("trusted", {
            "X-Forwarded-For": "198.51.100.99, 198.51.100.25",
            "X-Real-IP": "198.51.100.98", "X-Forwarded-Proto": "https",
            "Forwarded": "for=198.51.100.97",
        })
        self.assertEqual(data["ip"], "198.51.100.25")
        self.assertEqual(data["xff"], "198.51.100.25")
        self.assertEqual(data["forwarded"], "")

    def test_ipv6_client_is_preserved(self):
        data = self.echo_response("trusted", {"X-Forwarded-For": "2001:db8:1::25"})
        self.assertEqual(data["ip"], "2001:db8:1::25")

    def test_client_in_proxy_subnet_cannot_choose_an_earlier_forged_address(self):
        data = self.echo_response("trusted", {
            "X-Forwarded-For": "198.51.100.99, " + self.peer_ip,
        })
        self.assertEqual(data["ip"], self.peer_ip)
        self.assertEqual(data["xff"], self.peer_ip)

    def test_invalid_protocol_values_do_not_become_trusted(self):
        for value in ("https, http", "ftp"):
            with self.subTest(value=value):
                self.assertEqual(self.echo_response("trusted", {
                    "X-Forwarded-Proto": value,
                })["proto"], "http")

    def test_cookie_and_csrf_headers_reach_the_api(self):
        data = self.echo_response("trusted", {
            "Cookie": "JSESSIONID=synthetic-test-session", "X-CSRF-TOKEN": "synthetic-csrf",
        })
        self.assertEqual(data["cookie"], "JSESSIONID=synthetic-test-session")
        self.assertEqual(data["csrf"], "synthetic-csrf")

    def test_activation_route_is_served_by_the_spa(self):
        self.assertIn('id="root"', self.fetch("trusted", path="/activate").stdout)

    def test_invalid_or_all_address_trust_configuration_is_rejected(self):
        for value in ("0.0.0.0/0", "::/0", "10.0.0.0/999", "10.0.0.0/24;include /tmp/bad;"):
            with self.subTest(value=value):
                result = docker("run", "--rm", "--network", self.network,
                                "-e", "BACKEND_HOST=echo", "-e", "BACKEND_PORT=80",
                                "-e", "NGINX_TRUSTED_PROXY_CIDRS=" + value,
                                IMAGE, "nginx", "-t", check=False)
                self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
