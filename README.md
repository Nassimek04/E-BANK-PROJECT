# eBank — Microservices (Partie 1)

Projet de démonstration microservices avec **Spring Boot 4.1.2** et **Spring Cloud 2025.1.4** :
découverte de services, passerelle API, communication inter-services et tolérance aux pannes.

## Architecture

```text
                  Client
                    |
                    v
          gateway-service :9999
             /            \
            v              v
 customer-service      ebank-service
      :8056                :8057
            \              /
             \            /  (OpenFeign)
              v          v
        discovery-service :8761
```

## Services

| Service | Port | Rôle |
|---|---|---|
| `discovery-service` | 8761 | Serveur Eureka (annuaire des services) |
| `customer-service` | 8056 | Gestion des clients (H2 en mémoire) |
| `ebank-service` | 8057 | Gestion des comptes + appel Feign vers customer |
| `gateway-service` | 9999 | Routage dynamique via Eureka |

## Fonctionnalités

- Enregistrement et découverte automatiques des services avec **Eureka**
- Routage dynamique sans URL en dur avec **Spring Cloud Gateway**
- Communication inter-services avec **OpenFeign** (`@FeignClient`)
- Enrichissement des comptes avec les données du client
- Tolérance aux pannes avec **Resilience4j** (`@CircuitBreaker` + fallback)
- Documentation API avec **springdoc OpenAPI 3.0.0** (Swagger UI)

## Démarrage

Démarrer les services dans cet ordre (depuis chaque dossier) :

```powershell
.\mvnw.cmd spring-boot:run
```

1. `discovery-service`
2. `customer-service`
3. `ebank-service` — obligatoirement après customer (appel Feign au démarrage)
4. `gateway-service`

## URLs utiles

| Cible | URL |
|---|---|
| Dashboard Eureka | <http://localhost:8761> |
| Clients | <http://localhost:8056/customers> |
| Comptes | <http://localhost:8057/accounts> |
| Swagger customer | <http://localhost:8056/swagger-ui.html> |
| Swagger ebank | <http://localhost:8057/swagger-ui.html> |
| Clients via Gateway | <http://localhost:9999/CUSTOMER-SERVICE/customers> |
| Comptes via Gateway | <http://localhost:9999/EBANK-SERVICE/accounts> |

> Les noms d'application Eureka sont en **majuscules** dans les URLs du gateway.

## Tester le circuit breaker

1. Appeler un compte : `http://localhost:8057/accounts/{id}` → le client est renseigné.
2. Arrêter `customer-service`.
3. Rappeler la même URL → réponse **200** avec `"name": "Not available"`.
4. Redémarrer `customer-service` → les vraies données reviennent.

## Documentation détaillée

Voir [`PARTIE-1-MICROSERVICES.md`](PARTIE-1-MICROSERVICES.md) : configuration complète,
code des étapes, URLs, ordre de démarrage et dépannage.
