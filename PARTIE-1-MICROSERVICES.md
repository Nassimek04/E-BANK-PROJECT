# Partie 1 — Architecture microservices eBank

Documentation de la première partie du projet : découverte de services (Eureka), communication
inter-services (OpenFeign), passerelle API (Spring Cloud Gateway) et tolérance aux pannes
(Resilience4j Circuit Breaker).

---

## 1. Architecture

```text
                     Client / Navigateur
                             |
                             v
                +---------------------------+
                |  gateway-service  :9999   |
                +---------------------------+
                   /                       \
                  v                         v
   +----------------------+      +--------------------------+
   | customer-service     |      | ebank-service            |
   | :8056                |<-----| :8057  (OpenFeign)       |
   +----------------------+      +--------------------------+
                  \                         /
                   \                       /
                +---------------------------+
                |  discovery-service :8761  |   (annuaire Eureka)
                +---------------------------+
```

- **discovery-service** : serveur Eureka qui référence les instances des autres services.
- **customer-service** : gestion des clients (H2 en mémoire).
- **ebank-service** : gestion des comptes bancaires (H2 en mémoire) ; interroge `customer-service` via Feign.
- **gateway-service** : point d'entrée unique ; construit ses routes automatiquement à partir d'Eureka.

---

## 2. Stack technique

| Élément | Version / choix |
|---|---|
| Java | 21 (`discovery-service` déclare encore 17, compatible) |
| Spring Boot | 4.1.2 |
| Spring Cloud | 2025.1.4 |
| Eureka | `spring-cloud-starter-netflix-eureka-server` / `-client` |
| Gateway | `spring-cloud-starter-gateway-server-webflux` |
| OpenFeign | `spring-cloud-starter-openfeign` |
| Circuit Breaker | `spring-cloud-starter-circuitbreaker-resilience4j` (5.0.3) |
| Base de données | H2 en mémoire |
| Documentation API | springdoc OpenAPI **3.0.0** |
| Package de base | `g1.emsi.*` (adapté depuis le tutoriel `ma.enset.*`) |

---

## 3. Services et ports

| Service | Port | Rôle | Enregistré dans Eureka |
|---|---|---|---|
| `discovery-service` | 8761 | Serveur Eureka (dashboard) | non (c'est le serveur) |
| `customer-service` | 8056 | CRUD clients | oui |
| `ebank-service` | 8057 | CRUD comptes + appel Feign | oui |
| `gateway-service` | 9999 | Routage dynamique | oui |

Chaque service est un **projet Maven indépendant** (le `pom.xml` à la racine n'est pas un agrégateur).

---

## 4. Étape 1 — discovery-service (Eureka)

`DiscoveryServiceApplication.java`

```java
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}
```

`application.properties`

```properties
spring.application.name=discovery-service
server.port=8761

eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

- Dashboard : <http://localhost:8761>
- Les 3 services doivent y apparaître avec le statut `UP (1)`.

---

## 5. Étape 2 — customer-service

### Entité

```java
@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Customer {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private String email;
}
```

### Repository / Service

```java
public interface CustomerRepository extends JpaRepository<Customer, Long> { }
```

```java
@Service
@Transactional
@AllArgsConstructor
public class CustomerService {
    private CustomerRepository customerRepository;

    public List<Customer> getAllCustomers() { return customerRepository.findAll(); }

    public Customer findCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
    }

    public Customer saveCustomer(Customer customer) { return customerRepository.save(customer); }
}
```

### Contrôleur REST

| Méthode | URL | Description |
|---|---|---|
| GET | `/customers` | Liste des clients |
| GET | `/customers/{id}` | Un client |
| POST | `/customers` | Créer un client |

### Données de démonstration

`CommandLineRunner` insère 3 clients : **Mohamed**, **Imane**, **Yassine**
(emails `mohamed@gmail.com`, etc.).

### Configuration

```properties
spring.application.name=customer-service
server.port=8056

spring.datasource.url=jdbc:h2:mem:customers-db
spring.h2.console.enabled=true
spring.jpa.open-in-view=false

eureka.client.enabled=true
spring.cloud.config.enabled=false
```

---

## 6. Étape 3 — ebank-service

### Entité `BankAccount`

```java
@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BankAccount {
    @Id
    private String id;
    private Date createdAt;
    private double balance;
    private String type;
    private Long customerId;

    @Transient
    private Customer customer;   // rempli par Feign, non persisté
}
```

> Le `Customer` utilisé ici est un simple POJO (`g1.emsi.ebankservice.model.Customer`), pas une entité JPA.

### Repository

```java
public interface BankAccountRepository extends JpaRepository<BankAccount, String> {
    List<BankAccount> findByCustomerId(Long customerId);
}
```

### Contrôleur REST

| Méthode | URL | Description |
|---|---|---|
| GET | `/accounts` | Liste des comptes (sans enrichissement client) |
| GET | `/accounts/{id}` | Un compte **enrichi** avec le client (Feign) |
| POST | `/accounts` | Créer un compte (vérifie le client via Feign) |

### Données de démonstration

`EbankServiceApplication` insère **15 comptes** (3 clients × 5 comptes), types
`CURRENT_ACCOUNT` / `SAVING_ACCOUNT`, balance aléatoire entre 1 000 et 61 000.

### Configuration

```properties
spring.application.name=ebank-service
server.port=8057

spring.datasource.url=jdbc:h2:mem:accounts-db
spring.h2.console.enabled=true
spring.jpa.open-in-view=false

eureka.client.enabled=true
spring.cloud.config.enabled=false
```

---

## 7. Étape 4 — gateway-service (routage dynamique)

`GatewayServiceApplication.java`

```java
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }

    @Bean
    DiscoveryClientRouteDefinitionLocator dynamicRoutes(
            ReactiveDiscoveryClient rdc, DiscoveryLocatorProperties dlp) {
        return new DiscoveryClientRouteDefinitionLocator(rdc, dlp);
    }
}
```

`application.yml`

```yaml
server:
  port: 9999

spring:
  application:
    name: gateway-service
  cloud:
    gateway:
      server:
        webflux:
          # Routage dynamique via Eureka : les routes statiques sont désactivées.
          # routes:
          #   - id: r1
          #     uri: http://localhost:8056
          #     predicates:
          #       - Path=/customers/**
          #   - id: r2
          #     uri: http://localhost:8057
          #     predicates:
          #       - Path=/accounts/**

eureka:
  client:
    enabled: true
```

> Avec Gateway 5.x, le chemin de configuration des routes statiques est
> `spring.cloud.gateway.server.webflux.routes`.

### Convention d'URL du routage dynamique

Eureka expose les noms d'application **en majuscules** :

```text
http://localhost:9999/CUSTOMER-SERVICE/customers
http://localhost:9999/EBANK-SERVICE/accounts
```

Les variantes en minuscules (`/customer-service/...`) renvoient **404**.

> Après un redémarrage d'un service, le cache de routes du gateway peut mettre
> ~30 secondes à se rafraîchir.

---

## 8. Étape 5 — communication OpenFeign

### Activation

```java
@SpringBootApplication
@EnableFeignClients
public class EbankServiceApplication { ... }
```

### Client Feign

`ebank-service/src/main/java/g1/emsi/ebankservice/feign/CustomerRestClient.java`

```java
@FeignClient(name = "customer-service")
public interface CustomerRestClient {

    @GetMapping("/customers/{id}")
    Customer getCustomerById(@PathVariable("id") Long id);
}
```

Le nom `customer-service` est résolu par Eureka + Spring Cloud LoadBalancer
(aucune URL en dur).

### Utilisation dans `EBankService`

```java
public BankAccount getBankAccountById(String id) {
    BankAccount bankAccount = bankAccountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found"));
    Customer customer = customerRestClient.getCustomerById(bankAccount.getCustomerId());
    bankAccount.setCustomer(customer);
    return bankAccount;
}

public BankAccount saveAccount(BankAccount bankAccount) {
    Customer customer = customerRestClient.getCustomerById(bankAccount.getCustomerId());
    bankAccount.setId(UUID.randomUUID().toString());
    bankAccount.setCreatedAt(new Date());
    BankAccount savedAccount = bankAccountRepository.save(bankAccount);
    savedAccount.setCustomer(customer);   // évite "customer": null dans la réponse du POST
    return savedAccount;
}
```

> `save()` fait un `merge()` (l'id est déjà renseigné) et renvoie une autre instance :
> il faut réaffecter le `customer` sur l'objet retourné.

### Exemple de création

```http
POST http://localhost:8057/accounts
Content-Type: application/json

{
  "type": "CURRENT_ACCOUNT",
  "balance": 1500,
  "customerId": 2
}
```

---

## 9. Étape 6 — tolérance aux pannes (Resilience4j)

### Dépendance (`ebank-service/pom.xml`)

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

### Circuit Breaker + fallback

```java
@FeignClient(name = "customer-service")
public interface CustomerRestClient {

    @GetMapping("/customers/{id}")
    @CircuitBreaker(name = "customerService", fallbackMethod = "getDefaultCustomer")
    Customer getCustomerById(@PathVariable("id") Long id);

    default Customer getDefaultCustomer(Long id, Exception exception) {
        return Customer.builder()
                .id(id)
                .name("Not available")
                .email("Not available")
                .build();
    }
}
```

⚠️ Ordre des paramètres du fallback : **d'abord ceux de la méthode annotée**, puis l'`Exception`.

### Comportement obtenu

| Scénario | Résultat |
|---|---|
| customer-service actif | Le compte contient le vrai client |
| customer-service arrêté | **200** avec `"name": "Not available"` (plus de 500) |
| customer-service redémarré | Les vraies données reviennent automatiquement |

> Le fallback intercepte toutes les exceptions : un `POST /accounts` avec un `customerId`
> inexistant n'est plus rejeté, il crée le compte avec le client « Not available ».

### Réglages optionnels (circuit qui s'ouvre plus vite)

```properties
resilience4j.circuitbreaker.instances.customerService.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.customerService.sliding-window-size=5
resilience4j.circuitbreaker.instances.customerService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.customerService.wait-duration-in-open-state=10s
```

Avec la configuration par défaut (fenêtre de 100 appels), le circuit reste `CLOSED`
lors d'une panne courte, mais le fallback s'applique quand même à chaque appel.

---

## 10. URLs utiles

| Cible | URL |
|---|---|
| Dashboard Eureka | <http://localhost:8761> |
| Clients (direct) | <http://localhost:8056/customers> |
| Un client | <http://localhost:8056/customers/1> |
| Swagger customer | <http://localhost:8056/swagger-ui.html> |
| Comptes (direct) | <http://localhost:8057/accounts> |
| Swagger ebank | <http://localhost:8057/swagger-ui.html> |
| Clients via Gateway | <http://localhost:9999/CUSTOMER-SERVICE/customers> |
| Comptes via Gateway | <http://localhost:9999/EBANK-SERVICE/accounts> |
| Santé des services | `:8056/actuator/health`, `:8057/actuator/health`, `:9999/actuator/health` |

### Tester un compte enrichi

1. Récupérer un id :

```powershell
(Invoke-RestMethod http://localhost:8057/accounts)[0].id
```

2. Appeler le détail (direct ou via gateway) :

```text
http://localhost:8057/accounts/<ID>
http://localhost:9999/EBANK-SERVICE/accounts/<ID>
```

Réponse attendue :

```json
{
  "id": "67423e9b-aa3f-4eee-9254-0f6c118d5a1c",
  "createdAt": "2026-11-18T19:58:55.767Z",
  "balance": 1283.2793829926761,
  "type": "SAVING_ACCOUNT",
  "customerId": 1,
  "customer": {
    "id": 1,
    "name": "Mohamed",
    "email": "mohamed@gmail.com"
  }
}
```

> La liste `GET /accounts` ne renvoie pas le client (`customer: null`) :
> seul le détail déclenche l'appel Feign.

---

## 11. Ordre de démarrage

1. `discovery-service` (8761) — attendre que le dashboard réponde.
2. `customer-service` (8056).
3. `ebank-service` (8057) — **après** customer, car le `CommandLineRunner` appelle Feign au démarrage.
4. `gateway-service` (9999).

En ligne de commande, depuis chaque dossier de service :

```powershell
.\mvnw.cmd spring-boot:run
```

Tests :

```powershell
.\mvnw.cmd test
```

---

## 12. Dépannage

| Symptôme | Cause / solution |
|---|---|
| `Port 8057 is already in use` | Une autre instance tourne déjà. Arrêter le processus : `Get-NetTCPConnection -LocalPort 8057 -State Listen` puis `Stop-Process -Id <PID> -Force`. |
| Page **Whitelabel Error 500** sur `/accounts/<id>` | L'id appartient à un démarrage précédent. La base H2 est **en mémoire** : chaque redémarrage recrée les 15 comptes avec de nouveaux UUID. Reprendre un id via `GET /accounts`. |
| `404` sur `/EBANK-SERVICE/accounts` | Cache de routes du gateway non encore rafraîchi après un redémarrage (~30 s), ou nom d'application en minuscules (Eureka = majuscules). |
| Liens Eureka `localhost:*-service:PORT` → 404 | Ils pointent vers `/actuator/info`, non exposé (seul `health` l'est). Utiliser les URLs réelles du tableau §10. |
| Lombok : getters/setters introuvables dans IntelliJ | `Settings → Build, Execution, Deployment → Compiler → Annotation Processors` → cocher **Enable annotation processing**, puis **Build → Rebuild Project**. |
| Eureka vide au premier lancement | Attendre ~30 s (heartbeat) et rafraîchir <http://localhost:8761>. |
| `Cannot determine local hostname` dans les logs | Avertissement bénin (résolution du hostname local). |

### Rendre les liens Eureka fonctionnels (optionnel)

Exposer `info` dans les services :

```properties
management.endpoints.web.exposure.include=health,info
```

ou faire pointer la status page vers un endpoint existant :

```properties
eureka.instance.status-page-url-path=/actuator/health
```

---

## 13. Bilan de la Partie 1

- ✅ 4 services démarrés et enregistrés dans Eureka (`CUSTOMER-SERVICE`, `EBANK-SERVICE`, `GATEWAY-SERVICE`).
- ✅ Routage dynamique via Gateway, sans URL en dur.
- ✅ Communication inter-services OpenFeign résolue par Eureka.
- ✅ Enrichissement des comptes avec les données client.
- ✅ Tolérance aux pannes : fallback Resilience4j quand `customer-service` est indisponible.
- ✅ Swagger UI (springdoc 3.0.0) disponible sur customer et ebank.
- ✅ Builds Maven et tests : `BUILD SUCCESS` pour les 4 services.
