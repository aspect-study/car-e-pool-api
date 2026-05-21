# carpool-admin — Module Guide

Second Spring Boot module (port 8082) built with the **webforJ** UI framework. Depends on `carpool-service` only — does not pull in `carpool-bot` or `carpool-web`.

## Dependency Scope

`carpool-admin` depends on `carpool-service` only. It does not have access to `carpool-bot` or `carpool-web` beans. `AdminBeanConfig` provides a no-op `TelegramNotificationPort` bean to satisfy the interface without the Telegram adapter.

## webforJ Security Configurer

Security is delegated entirely to:

```java
WebforjSecurityConfigurer.webforj()
    .loginPage("/login", "/login")
    .logout("/logout", "/login")
```

**Do not add manual `.authorizeHttpRequests()` blocks after this.** The configurer internally adds `anyRequest()`, which causes a startup conflict if another `anyRequest()` rule follows it.

CSRF is currently disabled (`csrf.disable()`) — a known gap, not an oversight.

## Adding New Admin Views

New admin pages are webforJ `AppLayout` or `View` classes. Follow the existing pattern in the Login, Dashboard, and Hub Approval views. All data access goes through `carpool-service` beans injected via `@Autowired` or constructor injection — never access repositories directly from admin views.
