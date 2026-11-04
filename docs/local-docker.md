# Local Docker environment and networks

Local database configuration is read from a private `.env` in the repository root. Dockerfiles contain no passwords. Compose substitutes the environment values into MySQL and the backend at startup.

## Local startup

On a new checkout:

```sh
cp .env.example .env
```

Edit `.env` and set both password values. Its keys are:

```dotenv
MYSQL_DATABASE=flying_angels
MYSQL_USER=appuser
MYSQL_PASSWORD=
MYSQL_ROOT_PASSWORD=
```

Then start the stack:

```sh
docker compose up --build
```

For backend-only work, use `docker compose up --build mysql backend`. MySQL and backend use the same `MYSQL_USER` / `MYSQL_PASSWORD`. The health check reads the root password from the running container environment. Required values have no password fallback: Compose reports an error if either password or the username is missing/empty.

The private `.env` is Git-ignored and excluded from repository-root image build contexts. Commit only `.env.example`, with blank password fields. On the current checkout, `.env` was prepared privately using the generated values from the reverted secrets setup; no setup script is needed.

This is environment-variable configuration. It does not provide Docker Secrets: the local file is plaintext and the values are present in configured container environments. CI's disposable test fixture and AWS Secrets Manager configuration are separate.

## Current networking

The root Compose file declares two project-scoped bridge networks:

| Service | `application` | `database` |
| --- | --- | --- |
| Frontend | Yes | No |
| Backend | Yes | Yes |
| MySQL | No | Yes |

Backend connects to the database by service name, `mysql:3306`. Frontend and MySQL have no shared network. Compose creates the networks automatically with names such as `flying-angels_application` and `flying-angels_database`.

The applied layout is:

```yaml
services:
  frontend:
    networks:
      - application
  backend:
    networks:
      - application
      - database
  mysql:
    networks:
      - database

networks:
  application:
    driver: bridge
  database:
    driver: bridge
```

The frontend cannot resolve MySQL by its Compose service name through the application network. Published host ports remain a separate access path. Existing container names and published host ports are retained, including MySQL on port 3306 for host SQL tools. No secret mounts, configuration trees, Docker-specific Vite override or host-database override are applied.

For a fully container-based stack with no host SQL client, remove MySQL's `ports` entry and add `internal: true` to the database network. An internal-only database network does not provide host port forwarding. If retaining host SQL access, keep the database bridge non-internal and bind its port only to `127.0.0.1:3306:3306`. Application host ports can similarly bind to `127.0.0.1` for local-only access.

The existing Vite proxy targets `localhost:8080`. For Vite running inside the frontend container, its API target needs to be `http://backend:8080`; arrange that configuration with the frontend owner. This existing proxy issue is separate from network membership. React/Vite source and its original Docker command remain unchanged by this backend/Docker configuration change.

To activate the network configuration, recreate the containers and inspect the result:

```sh
docker compose config --quiet
docker compose up -d --build --force-recreate
docker network ls --filter label=com.docker.compose.project
docker compose ps
```

The existing MySQL tmpfs database is temporary: recreating/stopping its container loses its records. The `.env` file persists independently.

References: [Compose environment interpolation](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/), [Compose networks](https://docs.docker.com/reference/compose-file/networks/).
